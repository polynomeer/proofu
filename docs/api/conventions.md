---
owner: 백엔드 책임자
status: 계약 초안
review: API 릴리스 시
---

# API 공통 규칙

- 기본 경로는 `/api/v1`이며 JSON과 UTF-8을 사용합니다.
- 리소스 생성은 `201`, 비동기 작업 접수는 `202`와 `jobId`를 반환합니다.
- 오류는 RFC 9457 Problem Details 형식: `type`, `title`, `status`, `detail`, `instance`, `code`, `fieldErrors[]`.
- 페이지네이션은 안정된 cursor 방식이며 최대 크기를 제한합니다 (`?cursor=&limit=`, 기본 20, 최대 100).
- 변경 요청은 요청 ID(`X-Request-Id`)와 감사 actor를 기록하고 비밀 또는 원문 콘텐츠는 로그에서 제거합니다.
- 멱등성이 필요한 생성 API는 `Idempotency-Key` 헤더를 받습니다.
- 낙관적 잠금: 변경 요청은 `version`(또는 `revision`)을 포함하고 불일치 시 `409` + `code: CONFLICT_STALE_VERSION`.
- API 계약은 `packages/contracts/openapi.yaml`로 관리하고 호환성 검사를 CI에서 수행합니다.

## 오류 코드 (초안)

| code | status | 의미 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 필드 검증 실패, `fieldErrors` 포함 |
| `UNAUTHENTICATED` | 401 | 인증 필요 |
| `REAUTHENTICATION_REQUIRED` | 401 | 민감 작업에 최근 재인증 필요 |
| `FORBIDDEN_WORKSPACE` | 403 | workspace 경계 위반 |
| `NOT_FOUND` | 404 | 리소스 없음 (다른 workspace 리소스도 404) |
| `CONFLICT_STALE_VERSION` | 409 | 낙관적 잠금 실패 |
| `INVALID_STATUS_TRANSITION` | 409 | 허용되지 않은 지원 상태 전이 |
| `SNAPSHOT_IMMUTABLE` | 409 | 제출 스냅샷 변경 시도 |
| `UNSUPPORTED_CLAIM_IN_EXPORT` | 422 | 승인되지 않은 unsupported 문장 내보내기 |
| `RATE_LIMITED` | 429 | 속도 제한 |
| `AI_PROVIDER_UNAVAILABLE` | 503 | 모델 제공자 장애 |
