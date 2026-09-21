# 매칭·문서 생성·문장 개선 평가 시나리오

합성 지원 시나리오 하나로 F05(설명), F06(초안), 문장 개선 프롬프트를 실제 모델에 통과시킵니다. 실제 회사·개인 정보는 넣지 않습니다.

- `*.json`: `{ "posting": {title, company}, "requirements": [{id, category, text}], "claims": [{id, text, sourceText, status, evidence: [{id, title}], requirementIds, score}], "revision": {claimId, mode, mustKeepNumbers} }`
- 실행: `ANTHROPIC_API_KEY=... ./gradlew :ai-gateway:test --tests '*LivePromptEvalTest*' -i` 또는 `scripts/ai-live-check.sh`
- 기준(docs/ai/evaluation.md): 설명은 제안한 쌍마다 1개, 버린 출력 0건; 초안은 두 섹션 이상, 컨텍스트 밖 참조로 버린 블록 0건, certainty 상한 위반 0건(서버가 보정한 블록 0건); 문장 개선은 가드 통과(수치 보존).
