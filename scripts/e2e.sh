#!/usr/bin/env bash
# Runs the Playwright journeys against a real local stack: Postgres (docker), api + worker
# (oidc mode, fake AI provider), the mock IdP and a production build of the web app.
#   scripts/e2e.sh            # full run
#   E2E_KEEP=1 scripts/e2e.sh # leave the stack running afterwards
#   DATABASE_URL=... scripts/e2e.sh  # use a Postgres that is already running (CI service, other port)
set -euo pipefail
cd "$(dirname "$0")/.."
log="${E2E_LOG_DIR:-/tmp/proofu-e2e}"; mkdir -p "$log"
export AUTH_MODE=oidc AI_PROVIDER=fake
export OIDC_ISSUER=http://localhost:8181/realms/mock
export OIDC_JWKS_URI=http://localhost:8181/realms/mock/protocol/openid-connect/certs
export OIDC_CLIENT_ID=proofu-web OIDC_CLIENT_SECRET=e2e-secret
export SESSION_SECRET=e2e-session-secret-not-for-production-0123456789
export APP_ORIGIN=http://localhost:3111 API_ORIGIN=http://localhost:8080

pids=()
cleanup() {
  if [ -z "${E2E_KEEP:-}" ]; then
    for p in "${pids[@]}"; do kill "$p" 2>/dev/null || true; done
  fi
}
trap cleanup EXIT

if [ -z "${DATABASE_URL:-}" ]; then
  docker compose -f infra/docker-compose.yml up -d postgres >/dev/null
else
  echo "using DATABASE_URL from the environment; not starting the docker postgres"
fi
# A server left over from an earlier run would answer the health checks and the suite would
# silently test yesterday's build.
for port in 8080 8091 8181 3111; do
  if lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo "port $port is already in use; stop the leftover process (E2E_KEEP from a previous run?)" >&2
    exit 1
  fi
done

./gradlew :api:bootJar :worker:bootJar -q
pnpm --filter web build >"$log/web-build.log" 2>&1

node apps/web/scripts/mock-idp.mjs >"$log/mock-idp.log" 2>&1 & pids+=($!)
java -jar apps/api/build/libs/api-*[!plain].jar >"$log/api.log" 2>&1 & pids+=($!)

# The api owns the migrations; a worker that starts first polls a table that does not exist yet.
for i in $(seq 1 90); do
  curl -sf http://localhost:8080/actuator/health >/dev/null && break
  sleep 2
done
curl -sf http://localhost:8080/actuator/health >/dev/null || { echo "api did not start; see $log/api.log" >&2; exit 1; }

java -jar apps/worker/build/libs/worker-*[!plain].jar --server.port=8091 >"$log/worker.log" 2>&1 & pids+=($!)
(cd apps/web && exec pnpm start -p 3111) >"$log/web.log" 2>&1 & pids+=($!)

for i in $(seq 1 60); do
  curl -sf -o /dev/null http://localhost:3111/auth/signed-out && break
  sleep 2
done
curl -sf -o /dev/null http://localhost:3111/auth/signed-out || { echo "web did not start; see $log/web.log" >&2; exit 1; }

pnpm --filter e2e e2e "$@"
