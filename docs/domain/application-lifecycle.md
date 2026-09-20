---
owner: 도메인 책임자
status: 구현 기준
review: 도메인 변경 시
---

# 지원 상태 모델

```
INTERESTED -> PREPARING -> SUBMITTED
SUBMITTED -> DOCUMENT_PASSED | DOCUMENT_REJECTED | NO_RESPONSE | WITHDRAWN
DOCUMENT_PASSED -> HANDOFF_READY -> HANDED_OFF_TO_ITERVIEW
DOCUMENT_REJECTED | NO_RESPONSE -> REVIEW_PENDING -> REVIEWED
```

- 상태 변경은 **이벤트로 기록**하며 과거 상태를 덮어쓰지 않습니다 (`application_status_events`).
- 상태 전이는 허용 목록만 사용합니다. 도메인 모듈 `ApplicationStatus.canTransitionTo`가 단일 원천입니다.
- 서류 합격 이후 면접 진행 상태는 ProofU에서 관리하지 않고 iterview의 `handoffId`만 보관합니다.
- `WITHDRAWN`은 `INTERESTED`, `PREPARING`, `SUBMITTED`에서 가능합니다.

## 지원 보드 표시

| 상태      | 주요 정보              | 대표 행동             |
| --------- | ---------------------- | --------------------- |
| 관심      | 회사, 직무, 마감일     | 공고 분석             |
| 준비      | 진행률, 문서 버전      | 문서 편집             |
| 제출      | 제출일, 스냅샷         | 후속 일정 기록        |
| 서류 합격 | 결과일, 면접 인계 동의 | iterview로 전달       |
| 서류 탈락 | 결과일, 회고 상태      | 가설과 개선 행동 기록 |
| 종료      | 종료 사유              | 보관, 재사용          |

## 제출 스냅샷

제출은 `POST /applications/{id}/submissions`로만 하며 `SubmissionSnapshot.freeze`가 유일한 생성 경로입니다 (ADR-0006).

- 문서 버전은 같은 지원 건의 문서에 속해야 하고, `INFERRED`/`UNSUPPORTED` 블록이 승인되지 않았으면 거부합니다 (`UNSUPPORTED_CLAIM_IN_EXPORT`, 422).
- `INTERESTED`/`PREPARING`에서 제출하면 지원은 `SUBMITTED`로 전이합니다(`INTERESTED`는 `PREPARING`을 거쳐 이력을 남김). 이미 `SUBMITTED`이면 정정 스냅샷을 추가로 만들 뿐 전이하지 않습니다. 그 이후 상태는 제출을 받지 않습니다 (`ApplicationStatus.acceptsSubmission`).
- 제출 시각은 미래일 수 없습니다(5분 허용). `hash`는 버전 id·버전 내용 JSON·공고 스냅샷 해시를 정규화 없이 SHA-256한 값입니다.
- 스냅샷과 그것이 가리키는 `document_versions`·`job_posting_snapshots`는 모두 불변이므로 제출 당시 문서는 원천이 바뀌어도 동일하게 열립니다 (US06).

## 서류 탈락 회고 모델

| 필드      | 설명                            | 예시                                         |
| --------- | ------------------------------- | -------------------------------------------- |
| 관찰 사실 | 사용자가 직접 확인한 결과       | 제출 8일 후 불합격 이메일 수신               |
| 가설      | 가능하지만 확인되지 않은 원인   | 필수 경력 요구에 대한 근거가 약했을 수 있음  |
| 근거      | 가설을 지지하거나 반박하는 정보 | Requirement R3에 연결된 Evidence가 없음      |
| 신뢰도    | `LOW`, `MEDIUM`, `HIGH`         | 낮음                                         |
| 개선 행동 | 다음 지원에서 실행할 변경       | 유사 공고에는 운영 성과 Evidence를 우선 추가 |
| 검증 계획 | 가설을 확인할 다음 관찰         | 다음 3건의 서류 결과와 채택 문장 비교        |

시스템은 탈락 원인을 사실처럼 단정하지 않습니다. 가설·근거에 원인 단정 표현("때문에 탈락", "확실히", "분명히", "원인은" 등, `Review.DEFINITIVE_PHRASES`)이 있으면 저장은 허용하되 경고합니다.

회고는 `DOCUMENT_REJECTED`, `NO_RESPONSE`, `REVIEW_PENDING`, `REVIEWED` 상태에서만 작성할 수 있고(`ApplicationStatus.acceptsReview`), 첫 회고를 저장하면 지원은 `REVIEW_PENDING`을 거쳐 `REVIEWED`로 전이합니다.
