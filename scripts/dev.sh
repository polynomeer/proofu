#!/usr/bin/env bash
# The whole local stack in one terminal: Postgres (docker), api, worker, the mock IdP and the
# Next.js dev server. Starts the docker daemon if it is down, picks ports around whatever is
# already listening, streams every process to logs/dev/, and on Ctrl-C stops everything it
# started (and only that — a Postgres container that was already running stays up).
#
#   scripts/dev.sh                    # oidc mode with the mock IdP: login and logout work
#   scripts/dev.sh --auth header      # no login, seeded workspace via X-Workspace-Id
#   scripts/dev.sh --no-build         # reuse the jars from the last build
#   scripts/dev.sh --open             # open the browser once the web app answers
#   scripts/dev.sh --keep             # leave the stack running after Ctrl-C (scripts/stop.sh)
#   DATABASE_URL=jdbc:… scripts/dev.sh   # use a Postgres that is already running
#   AI_PROVIDER=anthropic scripts/dev.sh # real model calls; the key comes from .env
#
# A failed start writes logs/dev/failure-<timestamp>.log and prints its path.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

AUTH_MODE_ARG=oidc
BUILD=1
KEEP=
OPEN=
while [ $# -gt 0 ]; do
  case "$1" in
    --auth) AUTH_MODE_ARG="${2:-}"; shift 2 ;;
    --auth=*) AUTH_MODE_ARG="${1#*=}"; shift ;;
    --no-build) BUILD=; shift ;;
    --keep) KEEP=1; shift ;;
    --open) OPEN=1; shift ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done
case "$AUTH_MODE_ARG" in
  header|oidc) ;;
  *) die "--auth takes header or oidc, not '$AUTH_MODE_ARG'" ;;
esac

LOG_DIR="$REPO_ROOT/logs/dev"
PID_FILE="$LOG_DIR/dev.pids"
mkdir -p "$LOG_DIR"
[ -f "$PID_FILE" ] && [ -s "$PID_FILE" ] &&
  warn "logs/dev/dev.pids is left over from an earlier run; scripts/stop.sh cleans it up"
: >"$PID_FILE"
# scripts/stop.sh stops this process first so the cleanup below runs instead of its children
# being killed underneath it.
echo "supervisor $$" >>"$PID_FILE"

PROC_NAMES=""
STARTED_DB=
TAIL_PID=

# --- cleanup ------------------------------------------------------------------------------------

cleanup() {
  local code=$?
  trap - EXIT INT TERM
  [ -n "$TAIL_PID" ] && kill "$TAIL_PID" 2>/dev/null || true
  if [ -n "$KEEP" ]; then
    printf '\n'
    note "--keep: the stack stays up. Stop it with scripts/stop.sh."
    exit "$code"
  fi
  printf '\n'
  step "stopping"
  local name pid
  for name in $PROC_NAMES; do
    eval "pid=\${PID_$name:-}"
    [ -n "$pid" ] || continue
    if kill -0 "$pid" 2>/dev/null; then
      kill_tree_hard "$pid" 8
      ok "$name stopped"
    fi
  done
  if [ -n "$STARTED_DB" ]; then
    compose stop postgres >/dev/null 2>&1 && ok "postgres container stopped (data kept in the volume)"
  fi
  : >"$PID_FILE"
  exit "$code"
}
trap cleanup EXIT INT TERM

# start <name> <command…> — runs it in the background, everything into logs/dev/<name>.log.
start() {
  local name="$1"; shift
  local log="$LOG_DIR/$name.log"
  : >"$log"
  "$@" >"$log" 2>&1 &
  local pid=$!
  quiet_job "$pid"
  eval "PID_$name=$pid"
  PROC_NAMES="$PROC_NAMES $name"
  echo "$name $pid" >>"$PID_FILE"
}

logs_of() {
  local name out=""
  for name in $PROC_NAMES; do out="$out $LOG_DIR/$name.log"; done
  echo "$out"
}

abort() {
  local headline="$1"; shift
  local report
  report="$(fail_report "$LOG_DIR" "$headline" $(logs_of) "$@")"
  bad "$headline"
  bad "what happened is in $report"
  exit 1
}

# --- database -----------------------------------------------------------------------------------

step "database"
if [ -n "${DATABASE_URL:-}" ]; then
  ok "using DATABASE_URL from the environment; not touching docker"
  PG_PORT="$(echo "$DATABASE_URL" | sed -n 's|.*://[^:/]*:\([0-9]*\)/.*|\1|p')"
  PG_PORT="${PG_PORT:-5432}"
else
  ensure_docker
  if compose ps --status running --services 2>/dev/null | grep -qx postgres; then
    PG_PORT="$(compose port postgres 5432 2>/dev/null | sed 's/.*://')"
    [ -n "$PG_PORT" ] || die "the postgres container is running but publishes no host port"
    RESERVED_PORTS="$RESERVED_PORTS $PG_PORT"
    ok "reusing the running postgres container on port $PG_PORT"
  else
    reserve_port 5432; PG_PORT="$REPLY"
    [ "$PG_PORT" = 5432 ] || warn "5432 is taken by $(port_holder 5432); publishing postgres on $PG_PORT instead"
    export POSTGRES_PORT="$PG_PORT"
    compose up -d postgres >"$LOG_DIR/postgres.log" 2>&1 ||
      abort "the postgres container did not start" "$LOG_DIR/postgres.log"
    STARTED_DB=1
    waited=0
    until compose exec -T postgres pg_isready -U proofu -d proofu >/dev/null 2>&1; do
      [ "$waited" -lt 60 ] || abort "postgres did not become ready" "$LOG_DIR/postgres.log"
      sleep 1; waited=$((waited + 1))
    done
    ok "postgres started on port $PG_PORT"
  fi
  export DATABASE_URL="jdbc:postgresql://localhost:$PG_PORT/proofu"
fi

# --- ports --------------------------------------------------------------------------------------

step "ports"
reserve_port 8080; API_PORT="$REPLY"
reserve_port 8090; WORKER_PORT="$REPLY"
reserve_port 3000; WEB_PORT="$REPLY"
if [ "$AUTH_MODE_ARG" = oidc ]; then reserve_port 8181; IDP_PORT="$REPLY"; else IDP_PORT=; fi
for pair in "api:8080:$API_PORT" "worker:8090:$WORKER_PORT" "web:3000:$WEB_PORT" "idp:8181:${IDP_PORT:-8181}"; do
  name="${pair%%:*}"; rest="${pair#*:}"; want="${rest%%:*}"; got="${rest##*:}"
  [ "$want" = "$got" ] || note "$name: $want is taken by $(port_holder "$want") → $got"
done
ok "api $API_PORT · worker $WORKER_PORT · web $WEB_PORT${IDP_PORT:+ · mock IdP $IDP_PORT} · postgres $PG_PORT"

API_ORIGIN="http://localhost:$API_PORT"
APP_ORIGIN="http://localhost:$WEB_PORT"
ISSUER="http://localhost:${IDP_PORT:-8181}/realms/mock"
AI_PROVIDER="${AI_PROVIDER:-fake}"

FAIL_CONTEXT="auth mode: $AUTH_MODE_ARG
ai provider: $AI_PROVIDER
ports: api $API_PORT, worker $WORKER_PORT, web $WEB_PORT, idp ${IDP_PORT:-none}, postgres $PG_PORT
database: ${DATABASE_URL}"

# Real model calls need the key from the git-ignored .env; it is never printed or logged.
if [ "$AI_PROVIDER" != fake ]; then
  if [ -z "${ANTHROPIC_API_KEY:-}" ] && [ -f "$REPO_ROOT/.env" ]; then
    set -a; . "$REPO_ROOT/.env"; set +a
  fi
  [ -n "${ANTHROPIC_API_KEY:-}" ] || die "AI_PROVIDER=$AI_PROVIDER needs ANTHROPIC_API_KEY in .env or the environment"
  warn "AI_PROVIDER=$AI_PROVIDER: model calls cost money"
fi

# --- build --------------------------------------------------------------------------------------

if [ -n "$BUILD" ]; then
  step "build"
  if [ ! -d node_modules ]; then
    say "pnpm install"
    pnpm install >"$LOG_DIR/build-node.log" 2>&1 || abort "pnpm install failed" "$LOG_DIR/build-node.log"
  fi
  if [ ! -f packages/contracts/generated/api.d.ts ]; then
    say "generating the contract types"
    pnpm --filter contracts build >>"$LOG_DIR/build-node.log" 2>&1 ||
      abort "the contract types could not be generated" "$LOG_DIR/build-node.log"
  fi
  say "./gradlew :api:bootJar :worker:bootJar"
  ./gradlew :api:bootJar :worker:bootJar -q >"$LOG_DIR/build-jvm.log" 2>&1 ||
    abort "the api/worker build failed" "$LOG_DIR/build-jvm.log"
  ok "jars built"
else
  note "--no-build: using the jars from the last build"
fi
API_JAR="$(ls apps/api/build/libs/api-*[!plain].jar 2>/dev/null | head -1)"
WORKER_JAR="$(ls apps/worker/build/libs/worker-*[!plain].jar 2>/dev/null | head -1)"
[ -n "$API_JAR" ] && [ -n "$WORKER_JAR" ] || die "no jars to run; drop --no-build"

# --- start --------------------------------------------------------------------------------------

step "starting"

if [ "$AUTH_MODE_ARG" = oidc ]; then
  start idp env MOCK_IDP_PORT="$IDP_PORT" node apps/web/scripts/mock-idp.mjs
  wait_http "$ISSUER/.well-known/openid-configuration" 20 "$PID_idp" ||
    abort "the mock IdP did not start"
  ok "mock IdP on $IDP_PORT"
fi

start api env \
  DATABASE_URL="$DATABASE_URL" \
  AUTH_MODE="$AUTH_MODE_ARG" \
  OIDC_ISSUER="$ISSUER" \
  OIDC_JWKS_URI="$ISSUER/protocol/openid-connect/certs" \
  AI_PROVIDER="$AI_PROVIDER" \
  ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}" \
  java -jar "$API_JAR" --server.port="$API_PORT"

# The api owns the migrations, so the worker must not poll a jobs table that does not exist yet.
rc=0; wait_http "$API_ORIGIN/actuator/health" 120 "$PID_api" || rc=$?
case "$rc" in
  0) ok "api on $API_PORT" ;;
  2) abort "the api exited while starting up" ;;
  *) abort "the api did not answer /actuator/health within 120s" ;;
esac

start worker env \
  DATABASE_URL="$DATABASE_URL" \
  AI_PROVIDER="$AI_PROVIDER" \
  ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}" \
  java -jar "$WORKER_JAR" --server.port="$WORKER_PORT"

web_env=(
  AUTH_MODE="$AUTH_MODE_ARG"
  API_ORIGIN="$API_ORIGIN"
  APP_ORIGIN="$APP_ORIGIN"
)
if [ "$AUTH_MODE_ARG" = oidc ]; then
  web_env+=(
    OIDC_ISSUER="$ISSUER"
    OIDC_CLIENT_ID=proofu-web
    OIDC_CLIENT_SECRET=dev-secret
    # A fresh secret per run: every restart invalidates yesterday's session cookie.
    SESSION_SECRET="$(openssl rand -hex 32)"
  )
fi
start web env "${web_env[@]}" pnpm --filter web exec next dev -p "$WEB_PORT"

rc=0; wait_http "$APP_ORIGIN/auth/signed-out" 120 "$PID_web" || rc=$?
case "$rc" in
  0) ok "web on $WEB_PORT" ;;
  2) abort "the web dev server exited while starting up" ;;
  *) abort "the web dev server did not answer within 120s" ;;
esac
wait_http "http://localhost:$WORKER_PORT/actuator/health" 60 "$PID_worker" ||
  abort "the worker did not answer /actuator/health"
ok "worker on $WORKER_PORT"
# --- summary and watch --------------------------------------------------------------------------

printf '\n%s  proofu is up%s\n\n' "$C_BOLD" "$C_OFF"
printf '  %-10s %s\n' "web" "$APP_ORIGIN"
printf '  %-10s %s\n' "api" "$API_ORIGIN/api/v1  (docs $API_ORIGIN/api/v1/docs)"
printf '  %-10s %s\n' "worker" "http://localhost:$WORKER_PORT/actuator/health"
[ -n "$IDP_PORT" ] && printf '  %-10s %s\n' "mock IdP" "$ISSUER"
printf '  %-10s %s\n' "postgres" "localhost:$PG_PORT  (proofu/proofu, scripts/db.sh psql)"
printf '  %-10s %s\n' "auth" "$AUTH_MODE_ARG$([ "$AUTH_MODE_ARG" = oidc ] && echo ' — the mock IdP signs in dev@proofu.local without a password')"
printf '  %-10s %s\n' "ai" "$AI_PROVIDER"
printf '  %-10s %s\n' "logs" "logs/dev/{api,worker,web${IDP_PORT:+,idp}}.log"
printf '\n%sCtrl-C stops everything this script started.%s\n\n' "$C_DIM" "$C_OFF"

[ -n "$OPEN" ] && { command -v open >/dev/null 2>&1 && open "$APP_ORIGIN" || true; }

tail -n 0 -F $(logs_of) &
TAIL_PID=$!
quiet_job "$TAIL_PID"

# A dead server is worth a report, not a terminal that keeps tailing nothing.
while :; do
  for name in $PROC_NAMES; do
    eval "pid=\${PID_$name}"
    kill -0 "$pid" 2>/dev/null || abort "$name exited on its own"
  done
  sleep 2
done
