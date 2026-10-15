#!/usr/bin/env bash
# Runs the Playwright journeys against a real local stack: Postgres (docker), api + worker
# (oidc mode, fake AI provider), the mock IdP and a production build of the web app.
#   scripts/e2e.sh            # full run
#   E2E_KEEP=1 scripts/e2e.sh # leave the stack running afterwards
#   DATABASE_URL=... scripts/e2e.sh  # use a Postgres that is already running (CI service, other port)
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"
log="${E2E_LOG_DIR:-/tmp/proofu-e2e}"; mkdir -p "$log"
# Ports are chosen around whatever is already listening, so a machine where another project
# owns 8080 can still run the suite. Everything downstream reads them from here: the jars take
# --server.port, the web build takes the origins, and the specs take E2E_* (helpers.ts,
# playwright.config.ts).
reserve_port 8080; API_PORT="$REPLY"
reserve_port 8091; WORKER_PORT="$REPLY"
reserve_port 8181; IDP_PORT="$REPLY"
reserve_port 3111; WEB_PORT="$REPLY"

export AUTH_MODE=oidc AI_PROVIDER=fake
export MOCK_IDP_PORT="$IDP_PORT"
export OIDC_ISSUER="http://localhost:$IDP_PORT/realms/mock"
export OIDC_JWKS_URI="http://localhost:$IDP_PORT/realms/mock/protocol/openid-connect/certs"
export OIDC_CLIENT_ID=proofu-web OIDC_CLIENT_SECRET=e2e-secret
export SESSION_SECRET=e2e-session-secret-not-for-production-0123456789
export APP_ORIGIN="http://localhost:$WEB_PORT" API_ORIGIN="http://localhost:$API_PORT"
export E2E_BASE_URL="$APP_ORIGIN"
export E2E_API_URL="$API_ORIGIN/api/v1"
export E2E_IDP_URL="$OIDC_ISSUER"

pids=()
cleanup() {
  if [ -z "${E2E_KEEP:-}" ]; then
    # ${pids[@]+…}: an empty array is "unbound" under set -u on bash 3.2, and a cleanup that
    # dies there hides whatever actually failed.
    for p in ${pids[@]+"${pids[@]}"}; do kill_tree_hard "$p" 5; done
  fi
}
trap cleanup EXIT

if [ -z "${DATABASE_URL:-}" ]; then
  # 5432 often belongs to another project; publish on the first free port above it and tell the
  # api and worker where it landed.
  reserve_port 5432
  export POSTGRES_PORT="$REPLY"
  export DATABASE_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/proofu"
  [ "$POSTGRES_PORT" = 5432 ] || echo "5432 is taken by $(port_holder 5432); using $POSTGRES_PORT"
  docker compose -f infra/docker-compose.yml up -d postgres >/dev/null
else
  echo "using DATABASE_URL from the environment; not starting the docker postgres"
fi
./gradlew :api:bootJar :worker:bootJar -q
# The contract types are generated, not committed. The web build runs outside turbo on
# purpose: turbo's strict env mode would drop AUTH_MODE and the OIDC settings exported above,
# and the web app reads them at build time.
if ! { pnpm --filter @proofu/contracts build && pnpm --filter web build; } >"$log/web-build.log" 2>&1; then
  tail -40 "$log/web-build.log" >&2
  exit 1
fi

node apps/web/scripts/mock-idp.mjs >"$log/mock-idp.log" 2>&1 & pids+=($!)
java -jar apps/api/build/libs/api-*[!plain].jar --server.port="$API_PORT" >"$log/api.log" 2>&1 & pids+=($!)

# The api owns the migrations; a worker that starts first polls a table that does not exist yet.
for i in $(seq 1 90); do
  curl -sf "$API_ORIGIN/actuator/health" >/dev/null && break
  sleep 2
done
curl -sf "$API_ORIGIN/actuator/health" >/dev/null || { echo "api did not start; see $log/api.log" >&2; exit 1; }

java -jar apps/worker/build/libs/worker-*[!plain].jar --server.port="$WORKER_PORT" >"$log/worker.log" 2>&1 & pids+=($!)
(cd apps/web && exec pnpm start -p "$WEB_PORT") >"$log/web.log" 2>&1 & pids+=($!)

for i in $(seq 1 60); do
  curl -sf -o /dev/null "$APP_ORIGIN/auth/signed-out" && break
  sleep 2
done
curl -sf -o /dev/null "$APP_ORIGIN/auth/signed-out" || { echo "web did not start; see $log/web.log" >&2; exit 1; }
echo "stack: web $WEB_PORT · api $API_PORT · worker $WORKER_PORT · idp $IDP_PORT · postgres ${POSTGRES_PORT:-external}"

pnpm --filter e2e e2e "$@"
