---
owner: 제품과 데이터 책임자
status: 측정 기준
review: 실험 또는 지표 변경 시
---

# 제품 분석

## 이벤트 추적 계획

| 이벤트                     | 핵심 속성                                       | 수집 금지            |
| -------------------------- | ----------------------------------------------- | -------------------- |
| career_entry_created       | type, source, completionState                   | 경력 본문, 회사 기밀 |
| evidence_linked            | evidenceType, verification, sensitivity         | 파일 원문, URL 토큰  |
| job_posting_analyzed       | source, requirementCount, duration, status      | 공고 전체 본문       |
| match_reviewed             | requirementCategory, accepted, rejected         | Evidence 원문        |
| document_generated         | documentType, templateVersion, duration, status | 생성 문장 원문       |
| document_exported          | format, pageCount, status                       | 파일 내용            |
| application_status_changed | from, to, elapsedDays                           | 회사 담당자 개인정보 |
| review_completed           | hypothesisCount, actionCount, confidenceBand    | 자유 서술 원문       |

## 실험 원칙

- 지원 결과는 외부 요인의 영향을 크게 받으므로 단일 사례에서 인과를 확정하지 않습니다.
- 실험은 작성 시간, 근거 연결률, 사용자 채택률처럼 제품이 직접 영향을 주는 지표를 우선합니다.
- 합격률을 사용할 때는 직군, 경력 수준, 지원 채널, 표본 크기를 함께 해석합니다.
- 사용자의 민감한 경력 원문을 분석 도구에 보내지 않고 필요한 범주와 집계만 수집합니다.
