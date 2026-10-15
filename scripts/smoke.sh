#!/usr/bin/env bash
# What a deployment must look like from outside, checked without credentials: the api refuses
# anonymous calls and publishes no API docs, the web app sends a visitor to the IdP and carries
# its security headers, and both report healthy.
#
#   scripts/smoke.sh --web https://proofu.example --api https://api.proofu.example
#   scripts/smoke.sh --web http://localhost:3000 --worker http://localhost:8090
#
# --api and --worker are optional: deployments move the management port to the internal network
# (docs/operations/monitoring.md), so run those two from inside it. Anything that needs a login
# — the career list, posting analysis, an export — is scripts/e2e.sh's job, not this script's.
#
# Exits 1 on the first failed expectation. Nothing here writes, and no credential is sent.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

WEB=
API=
WORKER=
while [ $# -gt 0 ]; do
  case "$1" in
    --web) WEB="${2:-}"; shift 2 ;;
    --web=*) WEB="${1#*=}"; shift ;;
    --api) API="${2:-}"; shift 2 ;;
    --api=*) API="${1#*=}"; shift ;;
    --worker) WORKER="${2:-}"; shift 2 ;;
    --worker=*) WORKER="${1#*=}"; shift ;;
    -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done
[ -n "$WEB$API$WORKER" ] || die "give at least one of --web, --api, --worker (try --help)"
FAILED=0

# status <what> <expected codes, space separated> <url> [curl args…]
status() {
  local what="$1" expected="$2" url="$3"; shift 3
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$@" "$url" || echo 000)"
  case " $expected " in
    *" $code "*) ok "$what ($code)" ;;
    *) bad "$what: expected $expected, got $code — $url"; FAILED=$((FAILED + 1)) ;;
  esac
}

# header <what> <url> <header name> <expected substring>
header() {
  local what="$1" url="$2" name="$3" want="$4" value
  value="$(curl -s -o /dev/null -D - --max-time 20 "$url" 2>/dev/null |
    awk -v h="$(echo "$name" | tr 'A-Z' 'a-z')" 'BEGIN{IGNORECASE=1} tolower($1) == h":" { $1=""; print; exit }' |
    sed 's/^ *//; s/\r$//')"
  case "$value" in
    *"$want"*) ok "$what" ;;
    "") bad "$what: no $name header on $url"; FAILED=$((FAILED + 1)) ;;
    *) bad "$what: $name is '$value', expected it to contain '$want'"; FAILED=$((FAILED + 1)) ;;
  esac
}

if [ -n "$API" ]; then
  step "api $API"
  status "health is UP" "200" "$API/actuator/health"
  status "readiness probe" "200" "$API/actuator/health/readiness"
  status "metrics scrape" "200" "$API/actuator/prometheus"
  # Everything below is the deployment's security posture, not a feature test.
  status "an anonymous read is refused" "401" "$API/api/v1/career-entries"
  # Authentication comes before routing: an unknown path must not answer 404 to a stranger
  # either, or the 404s map the surface for them.
  status "an unknown path is refused the same way" "401" "$API/api/v1/nope"
  status "the api docs are off" "401 403 404" "$API/api/v1/openapi.json"
  status "swagger-ui is off" "401 403 404" "$API/api/v1/docs"
  status "no env endpoint" "401 403 404" "$API/actuator/env"
  status "no heap dump endpoint" "401 403 404" "$API/actuator/heapdump"
fi

if [ -n "$WORKER" ]; then
  step "worker $WORKER"
  status "health is UP" "200" "$WORKER/actuator/health"
  status "metrics scrape" "200" "$WORKER/actuator/prometheus"
  status "no api surface" "401 403 404" "$WORKER/api/v1/career-entries"
fi

if [ -n "$WEB" ]; then
  step "web $WEB"
  status "a visitor without a session is sent to the login" "307 302" "$WEB/"
  # proxy.ts gates every page, so an unknown one is a redirect too — a 404 here would mean the
  # route was rendered before the session was checked.
  status "an unknown page is gated as well" "307 302" "$WEB/nope-$(date +%s)"
  status "the signed-out page renders" "200" "$WEB/auth/signed-out"
  header "content security policy with a nonce" "$WEB/auth/signed-out" "content-security-policy" "nonce-"
  header "framing refused" "$WEB/auth/signed-out" "x-frame-options" "DENY"
  header "no content sniffing" "$WEB/auth/signed-out" "x-content-type-options" "nosniff"
  header "referrer policy" "$WEB/auth/signed-out" "referrer-policy" "strict-origin"
  case "$WEB" in
    https://*) header "strict transport security" "$WEB/auth/signed-out" "strict-transport-security" "max-age" ;;
    *) note "http target: HSTS is only set on https responses" ;;
  esac
fi

printf '\n'
if [ "$FAILED" -gt 0 ]; then
  bad "$FAILED check(s) failed"
  exit 1
fi
ok "smoke test passed"
