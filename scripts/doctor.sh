#!/usr/bin/env bash
# Checks that this machine can run the stack and the test suites, and says exactly what to do
# about anything it cannot find. Read-only: it starts nothing and installs nothing.
#
#   scripts/doctor.sh
#
# Exits 1 if something is actually broken; warnings alone still exit 0.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

FAILURES=0
WARNINGS=0
fail() { bad "$*"; FAILURES=$((FAILURES + 1)); }
soft() { warn "$*"; WARNINGS=$((WARNINGS + 1)); }

step "toolchain"
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
  if [ "${java_major:-0}" = 21 ]; then ok "java $java_major"
  else soft "java is $java_major; the build targets 21 (always use ./gradlew, which picks the toolchain)"; fi
else
  fail "java not found; install a JDK 21 (e.g. brew install --cask corretto21)"
fi

if command -v node >/dev/null 2>&1; then
  node_major="$(node -v | sed 's/v\([0-9]*\).*/\1/')"
  if [ "$node_major" -ge 22 ]; then ok "node $(node -v)"
  else fail "node $(node -v) is too old; package.json requires >= 22"; fi
else
  fail "node not found; install node 22 or newer"
fi

if command -v pnpm >/dev/null 2>&1; then
  pnpm_major="$(pnpm -v | cut -d. -f1)"
  if [ "$pnpm_major" -ge 11 ]; then ok "pnpm $(pnpm -v)"
  else soft "pnpm $(pnpm -v); the lockfile is pnpm 11 (corepack enable)"; fi
else
  fail "pnpm not found; run 'corepack enable' or 'npm i -g pnpm@11'"
fi

command -v python3 >/dev/null 2>&1 && ok "python3 $(python3 -V 2>&1 | cut -d' ' -f2)" ||
  fail "python3 not found; scripts/check-enums.py and scripts/contract-diff.sh need it"
command -v curl >/dev/null 2>&1 && ok "curl" || fail "curl not found; the health checks need it"
command -v lsof >/dev/null 2>&1 && ok "lsof" || soft "lsof not found; port checks fall back to a plain connect"

step "docker"
if ! command -v docker >/dev/null 2>&1; then
  fail "docker not found; Postgres, Testcontainers and the e2e suite all need it"
elif docker_running; then
  ok "daemon is running"
  if compose ps --status running --services 2>/dev/null | grep -qx postgres; then
    ok "postgres container is up on port $(compose port postgres 5432 2>/dev/null | sed 's/.*://')"
  else
    note "postgres container is not running (scripts/dev.sh starts it)"
  fi
else
  soft "daemon is down; scripts/dev.sh starts it, ./gradlew check does not"
fi

step "workspace"
[ -d node_modules ] && ok "node_modules present" || fail "run pnpm install"
[ -f packages/contracts/generated/api.d.ts ] && ok "contract types generated" ||
  soft "packages/contracts/generated/api.d.ts missing; run pnpm --filter contracts build"
[ -d apps/api/src/main/resources/db/migration ] && ok "migrations copied into the api resources" ||
  note "migrations are copied by the api build"
if [ -d "$HOME/Library/Caches/ms-playwright" ] || [ -d "$HOME/.cache/ms-playwright" ]; then
  ok "playwright browsers installed"
else
  soft "no playwright browsers; run pnpm --filter e2e exec playwright install chromium before scripts/e2e.sh"
fi
if [ -f .env ]; then
  grep -q '^ANTHROPIC_API_KEY=.' .env && ok ".env has an ANTHROPIC_API_KEY (value never printed)" ||
    note ".env exists without an ANTHROPIC_API_KEY; AI_PROVIDER=fake is the default anyway"
else
  note "no .env; the local default is AI_PROVIDER=fake, so nothing is needed"
fi

step "ports"
busy=
for port in 3000 8080 8090 8181 5432; do
  if port_in_use "$port"; then
    busy=1
    note "$port is held by $(port_holder "$port") — dev.sh will move that service up one"
  fi
done
[ -n "$busy" ] || ok "3000, 8080, 8090, 8181 and 5432 are free"

printf '\n'
if [ "$FAILURES" -gt 0 ]; then
  bad "$FAILURES problem(s) to fix$([ "$WARNINGS" -gt 0 ] && echo ", $WARNINGS warning(s)")"
  exit 1
fi
if [ "$WARNINGS" -gt 0 ]; then
  warn "$WARNINGS warning(s), nothing blocking"
else
  ok "everything checks out"
fi
