#!/usr/bin/env bash
# Stops what scripts/dev.sh (or an interrupted scripts/e2e.sh) left behind: the processes in
# logs/dev/dev.pids and, unless told otherwise, the docker containers.
#
#   scripts/stop.sh              # kill this project's processes, stop the containers
#   scripts/stop.sh --keep-db    # leave postgres running
#   scripts/stop.sh --ports      # also kill whatever still listens on the dev ports (asks first)
#
# Without --ports nothing outside the pid file is killed: 8080 or 3000 may well belong to another
# project, and this script does not get to decide that.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

KEEP_DB=
PORTS=
while [ $# -gt 0 ]; do
  case "$1" in
    --keep-db) KEEP_DB=1; shift ;;
    --ports) PORTS=1; shift ;;
    -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

PID_FILE="$REPO_ROOT/logs/dev/dev.pids"

step "processes"
found=
if [ -s "$PID_FILE" ]; then
  # dev.sh knows how to clean up after itself; killing its children first would only make it
  # report them as crashes and write a failure log.
  supervisor="$(awk '$1 == "supervisor" { print $2 }' "$PID_FILE" | head -1)"
  if [ -n "$supervisor" ] && kill -0 "$supervisor" 2>/dev/null; then
    kill -TERM "$supervisor" 2>/dev/null || true
    waited=0
    while kill -0 "$supervisor" 2>/dev/null && [ "$waited" -lt 30 ]; do
      sleep 1
      waited=$((waited + 1))
    done
    kill -0 "$supervisor" 2>/dev/null && kill_tree_hard "$supervisor" 5
    ok "scripts/dev.sh (pid $supervisor) stopped and cleaned up after itself"
    found=1
  fi
  while read -r name pid; do
    [ -n "${pid:-}" ] || continue
    [ "$name" = supervisor ] && continue
    if kill -0 "$pid" 2>/dev/null; then
      kill_tree_hard "$pid" 8
      ok "$name (pid $pid) stopped"
      found=1
    fi
  done <"$PID_FILE"
  : >"$PID_FILE"
fi
[ -n "$found" ] || note "nothing running from logs/dev/dev.pids"

if [ -n "$PORTS" ]; then
  step "dev ports"
  for port in 3000 3111 8080 8090 8091 8180 8181; do
    if port_in_use "$port"; then
      holder="$(port_holder "$port")"
      pid="$(echo "$holder" | sed -n 's/.*pid \([0-9]*\)).*/\1/p')"
      printf '  %s is held by %s — kill it? [y/N] ' "$port" "$holder"
      read -r answer </dev/tty || answer=n
      case "$answer" in
        y|Y) [ -n "$pid" ] && kill_tree_hard "$pid" 5 && ok "$port freed" ;;
        *) note "$port left alone" ;;
      esac
    fi
  done
fi

step "containers"
if ! docker_running; then
  note "the docker daemon is down; nothing to stop"
elif [ -n "$KEEP_DB" ]; then
  note "--keep-db: containers left running"
else
  running="$(compose ps --status running --services 2>/dev/null || true)"
  if [ -n "$running" ]; then
    compose stop >/dev/null 2>&1
    ok "stopped: $(echo "$running" | tr '\n' ' ')(the postgres volume keeps its data)"
  else
    note "no containers running"
  fi
fi
