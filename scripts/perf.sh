#!/usr/bin/env bash
# Measures the read paths under load against the targets in
# docs/architecture/non-functional.md §성능 (일반 읽기 p95 500ms, 생성 요청 접수 1초).
#
#   scripts/perf.sh                      # seed 2000 rows per table, 10 VUs for 30s
#   scripts/perf.sh --rows 20000 --vus 30 --duration 1m
#   scripts/perf.sh --api http://localhost:8080   # measure an api that is already running
#
# It seeds a workspace of its own, starts an api on a free port (header auth, fake AI) unless
# one is given, runs k6 from its container image and keeps the summary in logs/perf/.
#
# A laptop is not production: these numbers catch regressions and gross pathologies, they do
# not certify the SLO. The same script run against a deployment does.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

ROWS=2000
VUS=10
DURATION=30s
API=
while [ $# -gt 0 ]; do
  case "$1" in
    --rows) ROWS="${2:-}"; shift 2 ;;
    --vus) VUS="${2:-}"; shift 2 ;;
    --duration) DURATION="${2:-}"; shift 2 ;;
    --api) API="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

LOG_DIR="$REPO_ROOT/logs/perf"
mkdir -p "$LOG_DIR"
WORKSPACE="00000000-0000-7000-8000-0000000000f0"
FAIL_CONTEXT="rows per table: $ROWS
vus: $VUS, duration: $DURATION
workspace: $WORKSPACE"

api_pid=
cleanup() {
  [ -n "$api_pid" ] && kill_tree_hard "$api_pid" 10
}
trap cleanup EXIT INT TERM

ensure_docker >/dev/null
db_describe
step "database"
ok "$DB_DESCRIPTION"

step "seed"
say "$ROWS rows per table"
db_exec psql -q -v ON_ERROR_STOP=1 -v workspace="'$WORKSPACE'" -v rows="$ROWS" \
  <infra/performance/seed.sql >"$LOG_DIR/seed.log" 2>&1 ||
  die "seeding failed; see $(fail_report "$LOG_DIR" "the performance workspace could not be seeded" "$LOG_DIR/seed.log")"
# psql prints the counts as an aligned table; a grep that matches nothing would end the
# script here under `set -e`, which is a silly way to fail a load test.
sed -n 's/^ \([a-z_]*\) *| *\([0-9]*\) *$/    \1 \2/p' "$LOG_DIR/seed.log" | sort || true

if [ -z "$API" ]; then
  step "api"
  jar="$(ls apps/api/build/libs/api-*[!plain].jar 2>/dev/null | head -1)"
  [ -n "$jar" ] || { say "./gradlew :api:bootJar"; ./gradlew :api:bootJar -q; jar="$(ls apps/api/build/libs/api-*[!plain].jar | head -1)"; }
  reserve_port 8080
  port="$REPLY"
  API="http://localhost:$port"
  # Header auth keeps the load on the paths being measured instead of on token verification;
  # the fake provider keeps an enqueued job from costing money.
  env AUTH_MODE=header AI_PROVIDER=fake DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:${DB_PORT:-5432}/${PGDATABASE:-proofu}}" \
    java -jar "$jar" --server.port="$port" >"$LOG_DIR/api.log" 2>&1 &
  api_pid=$!
  quiet_job "$api_pid"
  wait_http "$API/actuator/health" 120 "$api_pid" || die "the api did not start; see $LOG_DIR/api.log"
  ok "api on $port (header auth, fake AI)"
else
  note "measuring the api at $API"
fi

step "load"
say "$VUS VUs for $DURATION against $API"
# The container reaches the host api through host.docker.internal; the script's own URL stays
# localhost so the summary is readable.
container_base="$(echo "$API" | sed 's|//localhost|//host.docker.internal|')"
summary="$LOG_DIR/summary-$(date +%Y%m%d-%H%M%S).json"
if docker run --rm -i \
  -e "BASE_URL=$container_base" -e "WORKSPACE_ID=$WORKSPACE" -e "VUS=$VUS" -e "DURATION=$DURATION" \
  -v "$REPO_ROOT/infra/performance:/scripts:ro" \
  --add-host host.docker.internal:host-gateway \
  -v "$LOG_DIR:/out" \
  grafana/k6 run --summary-export=/out/summary.json /scripts/read-paths.js \
  >"$LOG_DIR/k6.log" 2>&1; then
  passed=1
else
  passed=
fi
cp "$LOG_DIR/summary.json" "$summary" 2>/dev/null || true
# The part of k6's own report that answers "did it meet the target".
sed -n '/THRESHOLDS/,$p' "$LOG_DIR/k6.log" | sed -n '1,45p'

printf '\n'
if [ -z "$passed" ]; then
  bad "the targets in docs/architecture/non-functional.md were not met"
  note "full output: $LOG_DIR/k6.log · summary: $summary"
  exit 1
fi
ok "within the targets (p95 read < 500ms, enqueue < 1s)"
note "summary: $summary"
