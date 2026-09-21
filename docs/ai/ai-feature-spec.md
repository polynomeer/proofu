---
owner: AI와 제품 책임자
status: 구현 기준
review: 모델 또는 프롬프트 변경 시
---

# AI 기능 범위

| 기능          | AI 역할                        | 사용자 승인             | 금지 사항                |
| ------------- | ------------------------------ | ----------------------- | ------------------------ |
| 공고 분석     | 요구사항 분류와 원문 구간 연결 | 추출 결과 승인          | 원문에 없는 조건 추가    |
| Evidence 매칭 | 후보 검색과 관련성 설명        | 선택 또는 제외          | 민감도 무시              |
| 문서 생성     | 선택된 사실의 표현과 배열      | 문장 단위 승인          | 새 성과, 수치, 기술 창작 |
| 문장 개선     | 길이, 명확성, 문체 조정        | 변경 비교 승인          | 사실 의미 변경           |
| 서류 회고     | 가능한 가설과 질문 제안        | 사용자가 사실·가설 구분 | 탈락 원인 확정           |

## 생성 출력 스키마

```json
{
  "blocks": [
    {
      "blockId": "...",
      "text": "...",
      "claimRefs": ["claim-id"],
      "evidenceRefs": ["evidence-id"],
      "requirementRefs": ["requirement-id"],
      "certainty": "supported | inferred | unsupported",
      "warnings": []
    }
  ]
}
```

## 모델과 비용 전략

- 작업 유형별 품질 최소 기준을 정의하고 가장 저렴한 통과 모델을 선택합니다.
- 분류와 추출은 소형 모델을 우선하고 최종 문서 생성은 상위 모델을 선택적으로 사용합니다.
- 입력 해시와 정책 버전이 같을 때 안전한 범위에서 결과를 캐시합니다.
- 워크스페이스별 월간 예산과 요청 속도 제한을 적용합니다.
- 제공자 장애 시 폴백 모델은 동일한 개인정보 지역 및 보존 조건을 충족해야 합니다.
- 제공자·모델·예산·지역 결정: [ADR-0008](../architecture/adr/0008-ai-provider-anthropic-claude.md). 기본 `claude-opus-5`, 작업별 `effort`(추출 low · 매칭 medium · 생성 high). 요구사항 추출은 모델이 돌려준 원문 인용문을 서버가 원문에서 찾아 `source_span`을 채운다(`RequirementExtractor`, 프롬프트 `extract-v3`). 평가 세트: `fixtures/ai/postings/`.
- 문장 개선(`SentenceReviser`, 프롬프트 `revise-v2`, `document.revision` 잡): 블록 하나와 그 블록이 인용한 Claim 문장만 컨텍스트로 주고 모드(`SHORTEN`·`CLARIFY`·`FORMAL`)에 따라 다시 쓴 문장을 받는다. 서버는 `RevisionGuard`로 원문·Claim에 없는 수치가 생기거나, 원문의 수치가 사라지거나, 비거나 3배 이상 길어진 제안을 버린다. 제안은 잡 결과로만 돌려주고 저장하지 않는다 — 사용자가 편집기에서 원문과 비교해 적용하고 버전을 저장해야 반영된다(참조·certainty 불변).
- 문서 생성(`DocumentDrafter`, 프롬프트 `draft-v3`)의 입력은 승인된 요구사항과 사용자가 **채택한** 매칭 후보 Claim(딸린 Evidence 포함)뿐이다. 모델은 섹션별 블록을 돌려주고, 서버가 블록 id(`<section>-<n>`)를 매기며 컨텍스트에 없는 id를 인용한 블록은 버린다(`dropped`). certainty는 인용 Claim의 근거 상태로 상한을 정한다(`GeneratedOutput.withCertaintyCeiling`).
