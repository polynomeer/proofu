#!/usr/bin/env bash
# Builds the three release images (api, worker, web) and, with --check, starts them under the
# production profile to prove they boot the way a deployment would.
#
#   scripts/images.sh                    # build proofu-api|worker|web:dev
#   scripts/images.sh --tag v0.2.0       # build with that tag
#   scripts/images.sh --check            # build, then boot all three and run scripts/smoke.sh
#   scripts/images.sh --only web         # one image
#   scripts/images.sh --platform linux/amd64
#
# The jars are built first with ./gradlew; the web image builds inside the container
# (infra/docker/*.Dockerfile, ADR-0012). A failure writes logs/images/failure-<time>.log.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

TAG=dev
ONLY=
CHECK=
PLATFORM=
while [ $# -gt 0 ]; do
  case "$1" in
    --tag) TAG="${2:-}"; shift 2 ;;
    --tag=*) TAG="${1#*=}"; shift ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    --only=*) ONLY="${1#*=}"; shift ;;
    --platform) PLATFORM="${2:-}"; shift 2 ;;
    --platform=*) PLATFORM="${1#*=}"; shift ;;
    --check) CHECK=1; shift ;;
    -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done
case "${ONLY:-all}" in all|api|worker|web) ;; *) die "--only takes api, worker or web" ;; esac

LOG_DIR="$REPO_ROOT/logs/images"
mkdir -p "$LOG_DIR"
REGISTRY="${PROOFU_IMAGE_PREFIX:-proofu}"
FAIL_CONTEXT="tag: $TAG
prefix: $REGISTRY
platform: ${PLATFORM:-host}"

wants() { [ -z "$ONLY" ] || [ "$ONLY" = "$1" ]; }

ensure_docker

if wants api || wants worker; then
  step "jars"
  targets=""
  if wants api; then targets="$targets :api:bootJar"; fi
  if wants worker; then targets="$targets :worker:bootJar"; fi
  say "./gradlew$targets"
  # shellcheck disable=SC2086
  ./gradlew $targets -q >"$LOG_DIR/jars.log" 2>&1 || {
    report="$(fail_report "$LOG_DIR" "the jars did not build" "$LOG_DIR/jars.log")"
    die "the jars did not build; see $report"
  }
  ok "jars built"
fi

build() { # build <name> <dockerfile>
  local name="$1" file="$2"
  # Separate statements: `local` expands all its words before it assigns any of them.
  local image="$REGISTRY-$name:$TAG"
  local log="$LOG_DIR/$name.log"
  printf '  %-8s %s ' "$name" "$image"
  local args=(build -f "$file" -t "$image")
  [ -n "$PLATFORM" ] && args+=(--platform "$PLATFORM")
  if docker "${args[@]}" . >"$log" 2>&1; then
    printf '%s✓%s %s\n' "$C_GREEN" "$C_OFF" "$(docker image inspect "$image" --format '{{.Size}}' |
      awk '{ printf "%.0f MB", $1 / 1048576 }')"
  else
    printf '%s✗%s\n' "$C_RED" "$C_OFF"
    local report
    report="$(fail_report "$LOG_DIR" "the $name image did not build" "$log")"
    die "see $report"
  fi
}

step "images"
if wants api; then build api infra/docker/api.Dockerfile; fi
if wants worker; then build worker infra/docker/worker.Dockerfile; fi
if wants web; then build web infra/docker/web.Dockerfile; fi

[ -n "$CHECK" ] || exit 0

# --- boot check -----------------------------------------------------------------------------
# The production profile is the one deployments use, so that is the one worth proving: header
# auth off, seeds off, springdoc off, the fake AI provider refused. The issuer is a mock IdP
# container and the model key a placeholder — nothing here calls a model, and the browser login
# journey is scripts/e2e.sh's job.
step "production boot check"
[ -z "$ONLY" ] || die "--check needs all three images; drop --only"

COMPOSE_CHECK="$REPO_ROOT/infra/docker-compose.release-check.yml"
PROJECT=proofu-check
reserve_port 58080; export CHECK_API_PORT="$REPLY"
reserve_port 58090; export CHECK_WORKER_PORT="$REPLY"
reserve_port 53000; export CHECK_WEB_PORT="$REPLY"
export CHECK_TAG="$TAG" CHECK_PREFIX="$REGISTRY"
FAIL_CONTEXT="$FAIL_CONTEXT
check ports: api $CHECK_API_PORT, worker $CHECK_WORKER_PORT, web $CHECK_WEB_PORT"

cleanup_check() {
  docker compose -f "$COMPOSE_CHECK" -p "$PROJECT" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup_check EXIT INT TERM

check_fail() {
  docker compose -f "$COMPOSE_CHECK" -p "$PROJECT" logs --no-color --tail 200 >>"$LOG_DIR/compose.log" 2>&1 || true
  die "see $(fail_report "$LOG_DIR" "$1" "$LOG_DIR/compose.log")"
}

docker compose -f "$COMPOSE_CHECK" -p "$PROJECT" up -d >"$LOG_DIR/compose.log" 2>&1 ||
  check_fail "the release-check stack did not start"

wait_http "http://localhost:$CHECK_API_PORT/actuator/health/readiness" 180 ||
  check_fail "the api image did not become ready under the production profile"
ok "api ready"
wait_http "http://localhost:$CHECK_WORKER_PORT/actuator/health/readiness" 120 ||
  check_fail "the worker image did not become ready under the production profile"
ok "worker ready"
wait_http "http://localhost:$CHECK_WEB_PORT/auth/signed-out" 120 ||
  check_fail "the web image did not answer under the production profile"
ok "web answering"

scripts/smoke.sh --web "http://localhost:$CHECK_WEB_PORT" \
  --api "http://localhost:$CHECK_API_PORT" \
  --worker "http://localhost:$CHECK_WORKER_PORT" || check_fail "the smoke test failed against the images"
