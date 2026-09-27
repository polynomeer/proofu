# Shared helpers for the scripts in this directory: output, ports, docker, process trees and
# failure reports. Sourced, never executed. Written for bash 3.2 (the one macOS ships).
#
#   source "$(dirname "$0")/lib/common.sh"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/infra/docker-compose.yml"

if [ -t 1 ]; then
  C_OFF=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BLUE=$'\033[34m'
else
  C_OFF=; C_BOLD=; C_DIM=; C_RED=; C_GREEN=; C_YELLOW=; C_BLUE=
fi

step() { printf '\n%s▸ %s%s\n' "$C_BOLD" "$*" "$C_OFF"; }
say() { printf '  %s\n' "$*"; }
note() { printf '  %s%s%s\n' "$C_DIM" "$*" "$C_OFF"; }
ok() { printf '  %s✓%s %s\n' "$C_GREEN" "$C_OFF" "$*"; }
warn() { printf '  %s!%s %s\n' "$C_YELLOW" "$C_OFF" "$*"; }
bad() { printf '  %s✗%s %s\n' "$C_RED" "$C_OFF" "$*" >&2; }
die() { bad "$*"; exit 1; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

# --- ports -------------------------------------------------------------------------------------

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1
  else
    # No lsof: a successful connect means somebody is listening.
    (exec 3<>"/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1 && exec 3>&-
  fi
}

# Who holds a port, for messages that would otherwise just say "busy".
port_holder() {
  local port="$1"
  command -v lsof >/dev/null 2>&1 || { echo "unknown"; return; }
  lsof -iTCP:"$port" -sTCP:LISTEN -n -P -F pcn 2>/dev/null |
    awk '/^p/{pid=substr($0,2)} /^c/{cmd=substr($0,2)} END{if (pid) printf "%s (pid %s)", cmd, pid; else print "unknown"}'
}

RESERVED_PORTS=""

# reserve_port <preferred> -> the first port from <preferred> up that nothing listens on and no
# earlier call took. Result in $REPLY, so callers keep the reservation (a $(…) subshell would
# lose it and hand the same port to two processes).
reserve_port() {
  local port="$1" tried=0
  while [ "$tried" -lt 100 ]; do
    case " $RESERVED_PORTS " in *" $port "*) port=$((port + 1)); tried=$((tried + 1)); continue;; esac
    if port_in_use "$port"; then
      port=$((port + 1)); tried=$((tried + 1)); continue
    fi
    RESERVED_PORTS="$RESERVED_PORTS $port"
    REPLY="$port"
    return 0
  done
  die "no free port within 100 of $1"
}

# --- docker ------------------------------------------------------------------------------------

# run_deadline <seconds> <command…> — the command's exit status, or 124 if it outlived the
# deadline. `docker info` never returns when Docker Desktop's engine is stopped but its socket
# symlink is still there (macOS), and a hung liveness check is worse than a failed one.
run_deadline() {
  local secs="$1"; shift
  local tmp pid waited=0 rc
  tmp="$(mktemp -t proofu-deadline)"
  ( "$@" >/dev/null 2>&1; echo $? >"$tmp" ) &
  pid=$!
  disown "$pid" 2>/dev/null || true
  while [ ! -s "$tmp" ]; do
    if [ "$waited" -ge "$secs" ]; then
      kill_tree "$pid" KILL
      rm -f "$tmp"
      return 124
    fi
    sleep 1
    waited=$((waited + 1))
  done
  rc="$(cat "$tmp")"
  rm -f "$tmp"
  return "$rc"
}

docker_running() { run_deadline "${1:-8}" docker info; }

# What to run to get the daemon up, or "" when this machine gives no clue.
docker_start_command() {
  if [ -n "${DOCKER_START_CMD:-}" ]; then echo "$DOCKER_START_CMD"; return; fi
  # Docker Desktop's own CLI knows how to start the engine; `open -a Docker` only launches the
  # app, and does nothing at all when its window is already open.
  if run_deadline 5 docker desktop status; then echo "docker desktop start"; return; fi
  if [ "$(uname -s)" = Darwin ]; then
    [ -d /Applications/OrbStack.app ] && { echo "open -ga OrbStack"; return; }
    [ -d /Applications/Docker.app ] && { echo "open -ga Docker"; return; }
  fi
  command -v colima >/dev/null 2>&1 && { echo "colima start"; return; }
  echo ""
}

# An app that is open with a stopped engine answers "already running" to every start command;
# only a restart revives it. Empty when there is nothing better to try.
docker_restart_command() {
  [ -n "${DOCKER_START_CMD:-}" ] && return
  run_deadline 5 docker desktop status && { echo "docker desktop restart"; return; }
  command -v colima >/dev/null 2>&1 && echo "colima restart"
}

run_detached() { ( eval "$1" >/dev/null 2>&1 ) & disown $! 2>/dev/null || true; }

# Starts the docker daemon when it is down. Never uses sudo: on Linux that would need the user's
# password, which these scripts do not ask for.
ensure_docker() {
  command -v docker >/dev/null 2>&1 ||
    die "docker is not installed. Install Docker Desktop, OrbStack or colima first."
  docker_running && { ok "docker daemon is running"; return 0; }

  local cmd retry started limit elapsed escalated=
  cmd="$(docker_start_command)"
  [ -n "$cmd" ] ||
    die "the docker daemon is down. Start it (linux: sudo systemctl start docker) or set DOCKER_START_CMD."
  retry="$(docker_restart_command)"

  warn "docker daemon is down; starting it ($cmd)"
  # Detached: a starter that never returns must not take the script with it.
  run_detached "$cmd"
  started=$(date +%s)
  limit="${DOCKER_START_TIMEOUT:-180}"
  while :; do
    if docker_running 3; then
      printf '\n'
      ok "docker daemon is up after $(( $(date +%s) - started ))s"
      return 0
    fi
    elapsed=$(( $(date +%s) - started ))
    if [ -z "$escalated" ] && [ -n "$retry" ] && [ "$elapsed" -ge 30 ]; then
      escalated=1
      printf '\n'
      warn "still no daemon after ${elapsed}s; the app is up with a stopped engine ($retry)"
      run_detached "$retry"
    fi
    [ "$elapsed" -lt "$limit" ] || break
    printf '.'
    sleep 2
  done
  printf '\n'
  die "the docker daemon did not come up within ${limit}s (see the Docker Desktop window, or DOCKER_START_TIMEOUT)"
}

# --- processes ---------------------------------------------------------------------------------

# Every pid in the subtree, children before parents. `pnpm` and `gradlew` both run the real
# server as a child, so killing only the pid we launched leaves a listener behind.
descendants() {
  local parent="$1" child
  for child in $(ps -o pid=,ppid= -A 2>/dev/null | awk -v p="$parent" '$2 == p { print $1 }'); do
    descendants "$child"
  done
  echo "$parent"
}

kill_tree() {
  local pid="$1" sig="${2:-TERM}" p
  kill -0 "$pid" 2>/dev/null || return 0
  for p in $(descendants "$pid"); do kill -"$sig" "$p" 2>/dev/null || true; done
}

# kill_tree_hard <pid> [grace seconds] — TERM the tree, then KILL whatever is still there.
kill_tree_hard() {
  local pid="$1" grace="${2:-8}" waited=0
  kill_tree "$pid" TERM
  while [ "$waited" -lt "$grace" ]; do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
    waited=$((waited + 1))
  done
  kill_tree "$pid" KILL
}

# quiet_job <pid> — drops the job from the table so bash does not print "Terminated: 15" over
# the script's own output when we stop it.
quiet_job() { disown "$1" 2>/dev/null || true; }

# wait_http <url> <timeout seconds> [pid] — true when the url answers. With a pid, gives up as
# soon as that process is gone instead of waiting out the timeout on a crashed server.
wait_http() {
  local url="$1" limit="$2" pid="${3:-}" waited=0
  while [ "$waited" -lt "$limit" ]; do
    curl -sf -o /dev/null "$url" && return 0
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then return 2; fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# --- failure reports ---------------------------------------------------------------------------

# fail_report <log dir> <headline> [log files…] — one file per failure holding the headline, the
# run's configuration and the tail plus error lines of every log, so a failed run leaves
# something to read after the terminal is gone. Echoes the path. $FAIL_CONTEXT is included when
# a script sets it (ports, modes — never secrets).
fail_report() {
  local dir="$1" headline="$2"; shift 2
  mkdir -p "$dir"
  local file="$dir/failure-$(date +%Y%m%d-%H%M%S).log" f
  {
    echo "$headline"
    echo
    echo "when:   $(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "where:  $REPO_ROOT"
    echo "commit: $(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)$( [ -n "$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null)" ] && echo ' (dirty)')"
    echo "os:     $(uname -sr)"
    [ -n "${FAIL_CONTEXT:-}" ] && { echo; echo "$FAIL_CONTEXT"; }
    for f in "$@"; do
      [ -f "$f" ] || continue
      echo
      echo "=== $f (last 120 lines) ==="
      tail -n 120 "$f"
      local hits
      hits="$(grep -nE 'ERROR|FATAL|Exception|Caused by|error TS[0-9]+|failed|FAIL' "$f" 2>/dev/null | tail -n 40 || true)"
      if [ -n "$hits" ]; then
        echo
        echo "--- $f: error lines ---"
        echo "$hits"
      fi
    done
  } >"$file" 2>&1
  echo "$file"
}
