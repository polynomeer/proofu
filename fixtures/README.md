# Fixtures

합성 테스트 데이터만 둡니다. 프로덕션 개인정보는 절대 복사하지 않습니다 (docs/testing/test-strategy.md).

- `ai/postings/` 합성 채용공고 원문과 기대 요구사항 (요구사항 추출 평가, 프롬프트 인젝션 사례 포함)
- `ai/scenarios/` 합성 경력·공고 시나리오 (매칭 설명·초안·문장 개선 평가)

실행기는 `packages/ai-gateway`의 `*EvalTest`입니다(docs/ai/evaluation.md). 파일 Evidence 샘플은
파일 업로드가 생기면 추가합니다.
