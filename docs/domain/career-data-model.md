---
owner: 도메인 책임자
status: 구현 기준
review: 도메인 변경 시
---

# 커리어 원천 데이터 모델

원천 데이터는 문서에 맞춰 복사한 문장이 아니라, 여러 지원에서 재사용할 수 있는 **사실 단위**로 저장합니다. 설명용 서술과 구조화 필드를 함께 보존하되, 기간과 수치는 원문 문자열뿐 아니라 정규화 값도 갖습니다.

| 영역           | 필수 필드                                      | 선택 필드                                     |
| -------------- | ---------------------------------------------- | --------------------------------------------- |
| Career Entry   | id, type, title, startDate, visibility, status | organization, endDate, location, description  |
| Project        | id, name, role, summary, period, visibility    | teamSize, links, technologies, careerEntryIds |
| Achievement    | id, action, outcome, confidence                | metricValue, metricUnit, baseline, timeframe  |
| Skill          | id, canonicalName, category                    | aliases, proficiency, lastUsedAt              |
| Capability     | id, name, definition                           | level, evidenceCriteria, parentId             |
| Learning       | id, type, title, completedAt                   | provider, hours, certificateId, notes         |
| Portfolio Item | id, title, artifactId, visibility              | summary, tags, publishedAt                    |

## Career Entry 유형

`EMPLOYMENT`, `EDUCATION`, `TRAINING`, `AWARD`, `CERTIFICATION`, `OTHER`

## 공개 범위 (Visibility)

`PRIVATE` (비공개), `SELECTIVE` (선택 공개), `PUBLIC` (전체 공개)

## 역량 체계

- 역량 분류는 기술 역량, 문제 해결, 시스템 설계, 실행, 협업, 리더십, 학습으로 시작합니다.
- 숙련도는 5단계로 표현하되 **자기평가와 Evidence 기반 평가를 분리**합니다.
- 사용자가 정의한 역량을 허용하며, 시스템 표준 역량과 매핑할 때 원래 이름을 보존합니다.
- 역량 수준은 단일 점수로 고정하지 않고 최근성, 반복성, 복잡도, 영향 범위를 함께 기록합니다.

| 수준        | 코드           | 행동 기준                          | Evidence 예시                    |
| ----------- | -------------- | ---------------------------------- | -------------------------------- |
| 1 입문      | `NOVICE`       | 가이드에 따라 제한된 작업 수행     | 학습 기록, 소규모 과제           |
| 2 실무      | `PRACTITIONER` | 일반적인 업무를 독립 수행          | 운영 코드, 기능 배포             |
| 3 독립 수행 | `INDEPENDENT`  | 모호한 문제를 분해하고 해결        | 프로젝트 책임, 성과 지표         |
| 4 고급      | `ADVANCED`     | 복잡한 시스템과 팀 의사결정에 영향 | 아키텍처 결정, 장애 개선, 멘토링 |
| 5 전략      | `STRATEGIC`    | 조직 수준의 방향과 기준을 설계     | 표준화, 조직 성과, 다팀 확산     |

## 불변식

- `end_date`는 `start_date` 이후여야 합니다.
- Project는 workspace 경계를 넘어 Career Entry에 연결하지 않습니다.
- Achievement에 수치(`metric_value`)가 있으면 단위(`metric_unit`)가 필요합니다.
- 원천 데이터 변경은 `revision`을 증가시키며 삭제는 기본적으로 soft delete입니다.
