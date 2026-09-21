#!/usr/bin/env bash
# Runs every opt-in live check against the real model and prints what it returned.
# Needs ANTHROPIC_API_KEY in the environment (never pass it as an argument or commit it).
set -euo pipefail
cd "$(dirname "$0")/.."
# A local .env (git-ignored) is the easiest place to keep the key; it is loaded, never printed.
if [ -z "${ANTHROPIC_API_KEY:-}" ] && [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi
if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  echo "ANTHROPIC_API_KEY is not set. Either export it in this shell or put it in .env (copy .env.example; .env is git-ignored)." >&2
  exit 2
fi
echo "Live checks: SDK smoke → extraction eval (fixtures/ai/postings) → explain/draft/revise eval (fixtures/ai/scenarios)"
echo "Expected cost: well under \$0.50 per run with claude-opus-5."
./gradlew :ai-gateway:test --rerun-tasks \
  --tests '*AnthropicModelClientLiveTest*' \
  --tests '*RequirementExtractionEvalTest*' \
  --tests '*LivePromptEvalTest*' \
  -i 2>&1 | grep -E '^(==|--|  |     |recall|model=|execution|BUILD|.*FAILED|.*Expecting|.*but )' \
  | grep -vE "Executed with|Not worth caching|SLF4J" || true
echo "Full report: packages/ai-gateway/build/reports/tests/test/index.html"
