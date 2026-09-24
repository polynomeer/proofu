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
- cursor는 keyset입니다. 시각으로 정렬하는 목록은 `InstantIdCursor`(`(ts, id) < (커서)`, 정렬 `ts desc, id desc`), 날짜 컬럼(경력·프로젝트 시작일)으로 정렬하는 목록은 `DateIdCursor`를 씁니다. **시각을 날짜로 묶지 않습니다** — Postgres의 `cast(ts as date)`는 세션 시간대를 따라 서버 시간대가 다르면 다음 페이지가 비고, 시간대 변환 함수는 STABLE이라 인덱스도 만들 수 없습니다.
- 목록 쿼리의 `ORDER BY`는 인덱스와 같은 모양이어야 합니다(`(workspace_id, ts DESC, id DESC) WHERE deleted_at IS NULL`). `ListQueryPlanTest`가 2만 행 워크스페이스에서 실행 계획을 확인해 정렬·순차 스캔이 끼어들면 실패합니다.
- 변경 요청은 요청 ID(`X-Request-Id`)와 감사 actor를 기록하고 비밀 또는 원문 콘텐츠는 로그에서 제거합니다.
- 멱등성이 필요한 생성 API는 `Idempotency-Key` 헤더를 받습니다.
- 낙관적 잠금: 변경 요청은 `version`(또는 `revision`)을 포함하고 불일치 시 `409` + `code: CONFLICT_STALE_VERSION`.
- API 계약은 `packages/contracts/openapi.yaml`로 관리하고 호환성 검사를 CI에서 수행합니다.

## 인증

[ADR-0010](../architecture/adr/0010-oidc-provider-and-session-model.md) 결정 1. `proofu.auth.mode`로 고릅니다.

- **`oidc`** (production 강제): API는 OIDC resource server입니다. `Authorization: Bearer <access token>`을 `OIDC_ISSUER`의 JWKS(`OIDC_JWKS_URI`, 첫 토큰에서 지연 로드)로 검증하고 `iss`·`exp`·`aud`(`OIDC_AUDIENCE`, 기본 `proofu-api`)를 확인합니다. `(iss, sub)`로 `users`를 찾고, 처음 보는 사용자는 workspace 1개와 함께 즉시 프로비저닝합니다(`OidcWorkspaceResolver`). `email_verified=false`는 401. 브라우저는 토큰을 갖지 않습니다 — `apps/web`이 BFF로서 암호화된 httpOnly 쿠키 세션을 갖고 `/api/*`를 프록시하며 토큰을 붙입니다.
- **`header`** (local·test 전용, production 불가): `X-Workspace-Id: <uuid>` 헤더로 workspace를 지정하며 호출자는 그 소유자로 취급됩니다 (`HeaderWorkspaceResolver`). `local` 프로필은 고정 workspace `00000000-0000-7000-8000-000000000002`를 시드합니다.

인증 실패는 두 모드 모두 `401 UNAUTHENTICATED`(Problem Details). 민감 작업(프로필 이메일 변경, AI 동의, 계정 삭제 `DELETE /me`, 전체 내보내기 다운로드 `GET /me/exports/{id}/file`)은 토큰의 `auth_time`이 `AUTH_REAUTH_MAX_AGE`(기본 10분) 이내여야 하고, 아니면 `401 REAUTHENTICATION_REQUIRED` — 웹은 `/auth/login?prompt=login&return=…`으로 재인증합니다. `header` 모드에는 `auth_time`이 없어 검사하지 않습니다.

## 오류 코드 (초안)

| code                          | status | 의미                                                                 |
| ----------------------------- | ------ | -------------------------------------------------------------------- |
| `VALIDATION_FAILED`           | 400    | 필드 검증 실패, `fieldErrors` 포함                                   |
| `UNAUTHENTICATED`             | 401    | 인증 필요                                                            |
| `REAUTHENTICATION_REQUIRED`   | 401    | 민감 작업에 최근 재인증 필요                                         |
| `FORBIDDEN_WORKSPACE`         | 403    | workspace 경계 위반                                                  |
| `NOT_FOUND`                   | 404    | 리소스 없음 (다른 workspace 리소스도 404)                            |
| `CONFLICT_STALE_VERSION`      | 409    | 낙관적 잠금 실패                                                     |
| `INVALID_STATUS_TRANSITION`   | 409    | 허용되지 않은 지원 상태 전이                                         |
| `SNAPSHOT_IMMUTABLE`          | 409    | 제출 스냅샷 변경 시도                                                |
| `UNSUPPORTED_CLAIM_IN_EXPORT` | 422    | 승인되지 않은 unsupported 문장 내보내기                              |
| `EXPORT_NOT_READY`            | 409    | 아직 렌더링되지 않았거나 실패한 내보내기 다운로드 시도               |
| `RATE_LIMITED`                | 429    | 속도 제한                                                            |
| `AI_PROVIDER_UNAVAILABLE`     | 503    | 모델 제공자 장애·과부하 (재시도 가능)                                |
| `AI_RATE_LIMITED`             | 429    | 모델 제공자 요청 한도 (재시도 가능)                                  |
| `AI_BILLING_BLOCKED`          | 503    | 모델 제공자 계정 크레딧 부족 — 운영자 조치 (`OPERATOR_ACTION` 로그)  |
| `AI_CONFIGURATION_ERROR`      | 503    | 모델 제공자 인증·요청 형식 오류 — 운영자 조치 (`OPERATOR_ACTION`)    |
| `AI_REFUSED`                  | 422    | 모델 제공자가 정책상 요청을 거절                                     |
| `AI_OUTPUT_INVALID`           | 502    | 모델 출력이 스키마·참조 검증에 실패해 채택하지 않음                  |
| `AI_BUDGET_EXCEEDED`          | 429    | 워크스페이스 월 예산·시간당 한도 또는 배포 일일 예산 초과 (ADR-0008) |
| `NOT_IMPLEMENTED`             | 501    | 계약에는 있으나 연동이 아직 없는 경로 (URL 수집, career-ops)         |
