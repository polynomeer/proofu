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

## 프로필

문서 머리글에 들어가는 사람 정보(`Profile`): 이름(필수), 한 줄 소개, 이메일, 전화, 지역, 링크(최대 5개). 사용자당 1개(`user_profiles`)이며 워크스페이스 소유자의 것을 씁니다. 프로필은 **내보내기 파일에만** 렌더링되고 문서 버전(`document_versions`)에는 저장되지 않으며, AI 컨텍스트에 절대 들어가지 않습니다. 프로필이 바뀌면 `version`이 오르고, 같은 문서 버전이라도 이전 프로필로 만든 내보내기 파일은 재사용하지 않습니다.

## Career Entry 유형

`EMPLOYMENT`, `EDUCATION`, `TRAINING`, `AWARD`, `CERTIFICATION`, `OTHER`

## 공개 범위 (Visibility)

`PRIVATE` (비공개), `SELECTIVE` (선택 공개), `PUBLIC` (전체 공개). 새 기록의 기본값은 워크스페이스 설정 `defaultVisibility`(초기값 `PRIVATE`)를 따르며, 요청이 값을 주면 그것이 우선합니다.

## 기술 (Skill)

기술은 **워크스페이스 안에서 이름 하나로 모이는 사실**입니다. 같은 기술을 공고마다 다른 이름으로 쓰기 때문에 대표 이름(`canonicalName`)과 별칭(`aliases`, 최대 20개)을 함께 보존합니다. 중복은 대소문자·공백을 무시하고 판정하며(`Skill.normalizeName`), 대표 이름과 별칭을 합쳐 한 워크스페이스 안에서 겹칠 수 없습니다.

| 필드            | 규칙                                                                               |
| --------------- | ---------------------------------------------------------------------------------- |
| `canonicalName` | 필수, 120자 이하. 같은 워크스페이스에서 대소문자 무시 유일                         |
| `category`      | 필수. 아래 분류 중 하나                                                            |
| `aliases`       | 0–20개, 각 120자 이하. 대표 이름과 같거나 서로 겹칠 수 없음                        |
| `proficiency`   | 선택. **자기평가**이며 역량 체계의 5단계를 그대로 쓴다 (Evidence 기반 평가와 분리) |
| `lastUsedAt`    | 선택. 미래 날짜 불가                                                               |

분류(`SkillCategory`): `PROGRAMMING_LANGUAGE`(프로그래밍 언어), `FRAMEWORK`(프레임워크·라이브러리), `PLATFORM`(플랫폼·인프라), `DATA`(데이터·분석), `TOOL`(도구), `METHOD`(방법론·프로세스), `DOMAIN`(도메인 지식), `SPOKEN_LANGUAGE`(외국어), `OTHER`(기타).

프로젝트와는 다대다(`project_skills`)로 연결하고, 연결은 프로젝트 쪽에서 **집합을 통째로 교체**합니다(`PUT /projects/{id}/skills`). 기술을 삭제하면 휴지통으로 가고 연결은 즉시 끊어지며, 보존 기간이 지나면 정리 잡이 지웁니다.

기술은 **매칭 점수에 쓰지 않습니다**. 점수 항목(`MatchFeatures`)을 바꾸려면 평가 세트를 다시 돌려야 하므로, 지금은 사람이 읽는 사실과 내보내기 자료로만 씁니다.

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
