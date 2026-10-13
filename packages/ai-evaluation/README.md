# ai-evaluation

AI 사실성 · 근거 충실도 · 관련성 · 안전성 평가의 자리입니다 (docs/ai/evaluation.md).

아직 이 패키지에는 코드가 없습니다. 평가 세트는 `fixtures/ai/`에 있고, 실행기는
`packages/ai-gateway`의 `*EvalTest`(`RequirementExtractionEvalTest`, `LivePromptEvalTest`)입니다.
`ANTHROPIC_API_KEY`가 있을 때만 돌며, 로컬은 `scripts/ai-live-check.sh`, CI는
`.github/workflows/ai-live-eval.yml`(`ai-live` 환경의 시크릿 필요)로 실행합니다.
평가 세트가 커지면 이 패키지로 분리합니다.
