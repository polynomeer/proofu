---
owner: 도메인 책임자
status: 구현 기준
review: 도메인 변경 시
---

# Evidence와 Claim 규칙

| 속성                  | 값                                                                                         | 규칙                                         |
| --------------------- | ------------------------------------------------------------------------------------------ | -------------------------------------------- |
| 유형                  | `FILE`, `URL`, `REPOSITORY`, `COMMIT`, `METRIC`, `CERTIFICATE`, `NOTE`, `REFERENCE_LETTER` | 원본 또는 원본 위치를 보존합니다.            |
| 검증 상태             | `UNVERIFIED`, `USER_VERIFIED`, `EXTERNALLY_VERIFIED`, `EXPIRED`                            | **AI는 검증 상태를 올릴 수 없습니다.**       |
| 관계 (Claim↔Evidence) | `SUPPORTS`, `REFUTES`, `PARTIALLY_SUPPORTS`                                                | 부분 지지는 적용 범위(`scope`)를 기록합니다. |
| 민감도                | `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`                                         | 문서 생성과 외부 전송 필터에 사용합니다.     |
| 신뢰도                | 0 ~ 1                                                                                      | 출처 품질과 최신성을 별도 근거로 기록합니다. |
| 출처                  | `USER_INPUT`, `EXTERNAL_IMPORT`, `SYSTEM_EXTRACTED`                                        | 자동 추출은 원문 위치를 포함합니다.          |

## 위치 규칙 (유형별)

| 유형                                                                               | 필수                                                            |
| ---------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| `NOTE`                                                                             | `body` (메모 본문)                                              |
| `FILE`                                                                             | `object_key` (객체 저장소, 스토리지 확정 전까지 API에서 미지원) |
| 그 외 (`URL`, `REPOSITORY`, `COMMIT`, `METRIC`, `CERTIFICATE`, `REFERENCE_LETTER`) | 절대 http(s) `uri`                                              |

`body`는 다른 유형에서 선택적 설명으로 쓸 수 있습니다. 사용자는 검증 상태를 `UNVERIFIED`, `USER_VERIFIED`, `EXPIRED` 사이에서만 바꿀 수 있고 `EXTERNALLY_VERIFIED`는 연동 전용입니다.

## Claim 원천

Claim은 어떤 경력 기록에 대한 주장인지 `claim_sources`로 가리키며 (`CAREER_ENTRY` | `PROJECT` | `ACHIEVEMENT`), 읽은 시점의 `revision`을 고정합니다. 같은 기록을 두 번 가리킬 수 없습니다.

## Claim 유형

`FACT` (사실), `INFERENCE` (추론), `OPINION` (의견)

## 핵심 불변식

> 승인된 지원 문장의 사실 주장은 최소 하나의 Claim을 참조해야 하며, Claim은 Evidence가 없으면 `UNSUPPORTED` 상태입니다. `UNSUPPORTED` 문장은 사용자에게 명시적으로 경고하고, **수치 성과는 Evidence 또는 사용자 재확인 없이 자동 출력하지 않습니다.**

- 다른 workspace의 Evidence를 Claim에 연결할 수 없습니다.
- 같은 (claim, evidence) 쌍의 관계 중복을 금지합니다.
- Evidence는 `uri` 또는 `object_key` 중 하나가 필요합니다.
- 민감도 `CONFIDENTIAL` 또는 `RESTRICTED` 데이터는 허용되지 않은 AI 컨텍스트에 포함되지 않습니다.
