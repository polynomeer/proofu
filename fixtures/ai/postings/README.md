# 요구사항 추출 평가 세트

합성 채용공고와 그 공고에서 반드시 뽑혀야 하는 요구사항(원문 인용) 목록입니다. 실제 회사·개인 정보는 넣지 않습니다.

- `*.json`: `{ "title", "text", "expected": [{ "category", "quote" }] }`
- 실행: `ANTHROPIC_API_KEY=... ./gradlew :ai-gateway:test --tests '*RequirementExtractionEvalTest*'`
- 기준(docs/ai/evaluation.md): 기대 인용 재현율 90% 이상, 원문에 없는 조건 0건.
