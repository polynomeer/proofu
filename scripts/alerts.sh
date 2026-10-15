#!/usr/bin/env bash
# Checks the Prometheus rules in infra/observability, runs their unit tests and confirms that
# every metric they query is one the code actually publishes — so an SLI in
# docs/operations/monitoring.md cannot drift from the rule that is supposed to enforce it, and
# a renamed meter cannot leave a rule evaluating nothing for ever.
#
#   scripts/alerts.sh            # promtool check rules + promtool test rules + name agreement
#
# promtool comes from the prometheus image; nothing is installed on this machine.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

IMAGE="${PROMTOOL_IMAGE:-prom/prometheus:v3.7.3}"
ensure_docker >/dev/null

promtool() {
  docker run --rm --entrypoint promtool -v "$REPO_ROOT/infra/observability:/rules:ro" -w /rules "$IMAGE" "$@"
}

step "alert rules"
promtool check rules alerts.yml | sed 's/^/  /'
promtool test rules alerts.test.yml | sed 's/^/  /'
python3 "$REPO_ROOT/scripts/check-metrics.py" | sed 's/^/  /'
ok "rules, their tests and the metric names agree"
