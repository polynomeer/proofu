#!/usr/bin/env bash
# Every gate a change has to pass, in one command, each step logged separately.
#
#   scripts/verify.sh            # format:check, pnpm check, gradlew check, enum agreement
#   scripts/verify.sh --fix      # run the formatters first (prettier, ktlintFormat)
#   scripts/verify.sh --e2e      # also the Playwright journeys (slow, builds the whole stack)
#   scripts/verify.sh --fail-fast
#
# Steps keep running after a failure so one command tells you everything that is wrong. Each
# step writes logs/verify/<step>.log, and a failing step also writes
# logs/verify/failure-<timestamp>.log with its tail and error lines.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

FIX=
E2E=
FAIL_FAST=
while [ $# -gt 0 ]; do
  case "$1" in
    --fix) FIX=1; shift ;;
    --e2e) E2E=1; shift ;;
    --fail-fast) FAIL_FAST=1; shift ;;
    -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

LOG_DIR="$REPO_ROOT/logs/verify"
mkdir -p "$LOG_DIR"
RESULTS=""
FAILED=0

# run <name> <command…> — one gate, its own log, a line of output either way.
run() {
  local name="$1"; shift
  local log="$LOG_DIR/$name.log" started elapsed
  printf '  %-18s ' "$name"
  started=$(date +%s)
  if "$@" >"$log" 2>&1; then
    elapsed=$(( $(date +%s) - started ))
    printf '%s✓%s %ss\n' "$C_GREEN" "$C_OFF" "$elapsed"
    RESULTS="$RESULTS$name ok\n"
  else
    elapsed=$(( $(date +%s) - started ))
    printf '%s✗%s %ss\n' "$C_RED" "$C_OFF" "$elapsed"
    local report
    report="$(fail_report "$LOG_DIR" "verify: $name failed" "$log")"
    grep -v '^[[:space:]]*$' "$log" | tail -n 3 | sed 's/^/      /' 
    say "  full output: $log"
    say "  report:      $report"
    RESULTS="$RESULTS$name FAILED\n"
    FAILED=$((FAILED + 1))
    [ -n "$FAIL_FAST" ] && summary_and_exit
  fi
  return 0
}

summary_and_exit() {
  printf '\n'
  if [ "$FAILED" -gt 0 ]; then
    bad "$FAILED gate(s) failed"
    printf "$RESULTS" | sed 's/^/    /'
    exit 1
  fi
  ok "all gates passed"
  exit 0
}

if [ -n "$FIX" ]; then
  step "formatting"
  run prettier pnpm format
  run ktlint ./gradlew ktlintFormat
fi

step "gates"
run format-check pnpm format:check
run enums python3 scripts/check-enums.py
run pnpm-check pnpm check
# Testcontainers needs a docker daemon; ./gradlew check does not start one itself.
ensure_docker >/dev/null 2>&1 || warn "no docker daemon: the JVM tests that use Testcontainers will fail"
run gradle-check ./gradlew check

# The contract gate compares the declared spec with a running api, so it only runs if one is up.
API_ORIGIN="${API_ORIGIN:-http://localhost:8080}"
if curl -sf -o "$LOG_DIR/runtime-openapi.json" "$API_ORIGIN/api/v1/openapi.json" 2>/dev/null; then
  run contract-diff scripts/contract-diff.sh packages/contracts/openapi.yaml "$LOG_DIR/runtime-openapi.json"
else
  printf '  %-18s %sskipped%s no api on %s (CI compares against a running one)\n' \
    "contract-diff" "$C_DIM" "$C_OFF" "$API_ORIGIN"
fi

if [ -n "$E2E" ]; then
  step "journeys"
  run e2e scripts/e2e.sh
fi

summary_and_exit
