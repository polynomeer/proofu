#!/usr/bin/env bash
# Runs every opt-in live check against the real model and prints what it returned.
# Needs ANTHROPIC_API_KEY in the environment (never pass it as an argument or commit it).
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  echo "ANTHROPIC_API_KEY is not set. Export it in your shell first (see .env.example)." >&2
  exit 2
fi
echo "Live checks: SDK smoke → extraction eval (fixtures/ai/postings) → explain/draft/revise eval (fixtures/ai/scenarios)"
echo "Expected cost: well under \$0.50 per run with claude-opus-5."
./gradlew :ai-gateway:test --rerun-tasks \
  --tests '*AnthropicModelClientLiveTest*' \
  --tests '*RequirementExtractionEvalTest*' \
  --tests '*LivePromptEvalTest*' \
  -i 2>&1 | grep -E '^(==|--|  |     |recall|model=|execution|Live checks|BUILD|.*FAILED|.*Expecting|.*but )' || true
echo "Full report: packages/ai-gateway/build/reports/tests/test/index.html"
