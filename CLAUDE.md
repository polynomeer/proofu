# ProofU

커리어 Source of Truth: 경력 사실과 Evidence를 축적하고, 채용공고 요구사항과 매칭해 **근거를 추적할 수 있는** 지원 문서를 만든다. 제출 스냅샷은 불변이며, 서류 결과는 사실/가설을 분리해 회고한다.

문서의 단일 원천은 `docs/` (원본 docx는 `docs/source/`). 도메인 규칙은 코드보다 먼저 문서를 바꾼다.

## 구조

| 경로                         | 내용                                                                                                                                    | 도구                        |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| `apps/web`                   | Next.js 16 App Router, Tailwind 4, 디자인 토큰 `src/styles/tokens.css`                                                                  | pnpm                        |
| `apps/api`                   | Spring Boot 4.1 REST API `/api/v1`, JPA(`ddl-auto=validate`), Flyway, springdoc                                                         | Gradle `:api`               |
| `apps/worker`                | 헤드리스 Spring Boot, `jobs` 테이블 폴링 (SKIP LOCKED), `JobHandler` 빈 등록                                                            | Gradle `:worker`            |
| `packages/domain`            | 순수 Kotlin 도메인 모델·불변식. **Spring 의존 금지**                                                                                    | Gradle `:domain`            |
| `packages/ai-gateway`        | 모델 제공자로 가는 유일한 문. 컨텍스트 정책·예산·스키마 검증·비용·`ai_executions` 기록. api/worker가 `AiGatewayFactory`로 동일하게 구성 | Gradle `:ai-gateway`        |
| `packages/document-renderer` | DOCX(Apache POI)·Markdown·JSON 렌더러와 텍스트 재추출 검증(`ExportValidator`). Spring 의존 금지 (ADR-0009)                              | Gradle `:document-renderer` |
| `apps/e2e`                   | Playwright 여정 테스트. `scripts/e2e.sh`가 실제 스택 + mock IdP + 가짜 AI로 실행                                                        | pnpm                        |
| `packages/contracts`         | `openapi.yaml` (단일 원천) → `generated/api.d.ts`                                                                                       | pnpm                        |
| `migrations/`                | Flyway SQL 단일 원천. api 빌드 시 `db/migration`으로 복사                                                                               | —                           |
| `docs/`                      | 제품·도메인·디자인·아키텍처·ADR                                                                                                         | —                           |

## 명령

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL 16 (localhost:5432, proofu/proofu)
pnpm install && pnpm check                          # web + contracts: lint, typecheck, test
pnpm dev --filter web                               # http://localhost:3000 (/api/* → :8080 rewrite)
./gradlew check                                     # domain/api/worker 테스트 + ktlint (Docker 필요: Testcontainers)
./gradlew :api:bootRun                              # http://localhost:8080, OpenAPI /api/v1/openapi.json
./gradlew ktlintFormat                              # Kotlin 포맷
pnpm format                                         # prettier
scripts/e2e.sh                                      # Playwright 여정 (docker + jar + mock IdP + web build)
```

JDK 21 필요. `gradle` 직접 실행 시 JDK 25가 잡히면 실패하므로 항상 `./gradlew` 사용.

## 규칙

- **커밋**: Conventional Commits (`feat|fix|docs|refactor|test|chore|ci|perf(scope): subject`), 한 커밋 = 한 논리 변경. scope: `web`, `e2e`, `api`, `worker`, `domain`, `ai`, `renderer`, `contracts`, `db`, `infra`, `docs`. 커밋 전 해당 영역 검사 통과.
- **도메인 불변식은 `packages/domain`에 한 번만** 구현하고 단위 테스트한다. API/워커/UI는 이를 호출한다 (예: `ApplicationStatus.transitionTo`, `GeneratedOutput.requireGrounded`). DB CHECK/트리거는 방어선이지 대체가 아니다.
- **enum 값**은 domain Kotlin enum ↔ `migrations/` CHECK ↔ `openapi.yaml` ↔ `docs/domain` 네 곳이 항상 일치해야 한다. 하나를 바꾸면 넷을 바꾼다. 앞의 셋은 `scripts/check-enums.py`(CI)가 강제한다 — 새 enum 컬럼을 만들면 그 스크립트의 `COLUMN_ENUM`에 어느 쪽이 값을 선언하는지 적어야 통과한다. 네 번째(문서 산문)는 사람이 본다.
- **스키마 변경**은 `migrations/V{n}__*.sql` 추가만 (expand → migrate → contract). 기존 파일 수정 금지. JPA 엔티티는 스키마를 만들지 않는다.
- **불변 테이블** (`submission_snapshots`, `job_posting_snapshots`, `application_status_events`, `provenance_links`, `document_versions`, `audit_events`)은 UPDATE/DELETE 하지 않는다. 정정은 새 행. 유일한 예외는 계정 삭제 잡 `account.purge`가 `SET LOCAL proofu.purge = 'on'` 안에서 하는 DELETE(V9). `DELETE /me`는 재인증 필수, 즉시 `deleted_at`으로 로그인 차단, `users`/`workspaces`는 개인정보를 지운 묘비로 남긴다(`docs/data/retention.md`).
- **보존 기간**: `workspace_settings.trash_retention_days`(7–365, 기본 30)·`export_retention_days`(1–90, 기본 7)는 `RetentionPolicy`(domain)로만 검증하고 `PUT /me/settings`의 `retention`으로 바꾼다. worker `RetentionSweeper`(시간마다, 잡 테이블 아님)가 workspace별로 휴지통의 **원천 데이터**(성과→프로젝트→경력→스킬→역량→Claim→Evidence→요구사항, 살아 있는 행이 참조하면 보류)를 영구 삭제하고 `exports`/`account_exports`를 `EXPIRED`로 바꾸며 공유되지 않는 `export_files`를 지운다. 공고·지원·문서는 불변 자식 때문에 계정 삭제 때만 지운다(`docs/data/retention.md`). 새 소프트 삭제 테이블을 추가하면 sweeper 순서에 넣는다.
- **전체 내보내기**: `POST /me/exports` → `account.export` 잡(workspace당 대기 중 하나). 워커가 workspace 소유 테이블 전부를 `data.json`, READY 문서 파일을 `exports/`로 묶은 ZIP을 `export_files`(`pg:account:<id>`)에 저장하고 `account_exports`에 sha256·크기·표별 행 수·만료(`export_retention_days`)를 기록한다. 다운로드는 `GET /me/exports/{id}/file`뿐이며 재인증 필수, READY가 아니거나 만료면 `EXPORT_NOT_READY`(409). 새 테이블을 추가하면 `AccountExportJobHandler.TABLES`와 `AccountPurgeJobHandler` 삭제 순서에도 넣는다.
- **계약 일치**: `openapi.yaml`의 오퍼레이션 목록과 런타임 `/api/v1/openapi.json`은 항상 같아야 하고 CI(`scripts/contract-diff.sh`)가 강제한다. 아직 못 만든 경로는 계약에서 빼는 대신 `PendingIntegrationsController`처럼 **501 `NOT_IMPLEMENTED`** 로 답한다.
- **API 오류**는 항상 Problem Details + `code` (`ErrorCode`). 다른 workspace 리소스는 403이 아니라 404. 컨트롤러는 `WorkspaceContext` 파라미터로 호출자 workspace를 받는다.
- **인증 (ADR-0010)**: `AUTH_MODE=oidc`면 API는 resource server(`SecurityConfig`, `OidcWorkspaceResolver`가 `(iss, sub)`로 첫 로그인 시 user+workspace 프로비저닝, `aud`=`proofu-api` 필수, `email_verified=false` 거부), 웹은 BFF(`app/api/[...path]` 프록시가 암호화 쿠키 세션의 bearer를 붙임, 브라우저는 토큰을 모름, `proxy.ts`가 세션 없으면 `/auth/login`). `AUTH_MODE=header`(local·test만)는 `X-Workspace-Id`(`HeaderWorkspaceResolver`) + 시드 workspace `00000000-0000-7000-8000-000000000002`. 민감 작업은 `WorkspaceContext.requireRecentAuthentication` → `REAUTHENTICATION_REQUIRED`. 로컬 IdP: `node apps/web/scripts/mock-idp.mjs`(로그인 폼 없음) 또는 compose `--profile auth`의 Keycloak. 제공자 선택은 ADR-0010 결정 2(대기). `production` 프로필(`application-production.yml`)은 헤더 인증·시드·가짜 AI·springdoc을 끄고 DB·키 env를 필수로 요구한다(`ProductionProfileTest`); 프로필 미설정 = local이므로 배포는 반드시 `SPRING_PROFILES_ACTIVE=production`.
- **API 패턴** (`apps/api/.../career` 참고): 요청 DTO는 nullable + Bean Validation(400 fieldErrors) → 도메인 객체 생성(422) → JPA 엔티티(`from`/`apply`/`toDomain`). 목록은 keyset cursor(시각 정렬은 `InstantIdCursor`, 날짜 컬럼 정렬은 `DateIdCursor`; 시각을 날짜로 묶지 않는다)와 그 정렬과 **같은 모양의 인덱스**(`ListQueryPlanTest`가 실행 계획으로 강제), 변경은 row lock + revision 비교(409), 모든 변경은 `AuditLog`(해시만) + 도메인 이벤트 발행.
- **기술(Skill)**: 워크스페이스에서 이름 하나당 한 행. 대시보드 "주요 기술"은 **기록이 뒷받침하는 순서**(연결된 프로젝트 수 → 마지막 사용)로 정렬하고 자기평가 수준은 그대로 표시만 한다 — 순서는 실력 판단이 아니다. 대표 이름과 별칭을 합쳐 대소문자·공백을 무시하고 겹치면 `CONFLICT_DUPLICATE`(409)로 막고(`Skill.allNames`, V13 부분 유니크 인덱스), 분류는 `SkillCategory` 네 곳이 일치해야 한다. `proficiency`는 **자기평가**이며 Evidence·AI가 올리지 않는다. 프로젝트 연결은 `PUT /projects/{id}/skills`로 집합을 통째로 교체하고, 기술을 삭제하면 연결을 즉시 지운다. 기술은 매칭 점수(`MatchFeatures`)에 넣지 않는다 — 점수 항목을 바꾸면 평가 세트를 다시 돌려야 한다.
- **역량(Capability)**: 이름 + **정의**(필수) + `CapabilityCategory`. 수준(`selfAssessedLevel`)은 자기평가뿐이고 `evidence_based_level`은 쓰지 않는다 — Evidence 개수를 수준으로 바꾸는 판단은 하지 않고 `CapabilityEvidenceStatus.of`(NONE/UNVERIFIED/VERIFIED)로 연결 상태만 파생한다. 상위 역량은 같은 워크스페이스여야 하고 자기 자신·자손을 상위로 두면 422. Evidence 연결은 `PUT /capabilities/{id}/evidence`로 집합 교체, 역량 삭제는 하위 역량까지 휴지통으로 보내고 연결을 지운다(`docs/domain/career-data-model.md` §역량).
- **Evidence/Claim 흐름**: Claim은 경력 기록(`ClaimSource`, revision 고정)에 대한 문장이고 상태(`UNSUPPORTED/SUPPORTED/CONTESTED`)는 저장하지 않고 `ClaimEvidenceLink.statusOf`로 파생한다. Evidence 위치 규칙은 유형별(NOTE→body, FILE→object_key, 그 외→절대 uri). FILE 등록은 객체 저장소 확정 전까지 API가 422로 거부한다.
- **채용공고**: `JobPosting`(가변 식별)과 `JobPostingSnapshot`(불변 원문) 분리. 스냅샷은 `JobPostingSnapshot.capture`로만 만들고 `ContentHash`(정규화 후 SHA-256)가 같으면 재사용, 다르면 새 행. 수동 붙여넣기만 구현; URL 수집·career-ops는 `NOT_IMPLEMENTED`(501).
- **지원 상태**: 전이는 `POST /applications/{id}/transitions` → `Application.transition`만. 응답의 `allowedTransitions`를 UI가 그대로 버튼으로 쓴다. `HANDED_OFF_TO_ITERVIEW`는 인계 엔드포인트 전용, `application_status_events`는 append-only.
- **제출 스냅샷**: `SubmissionSnapshot.freeze`만이 생성 경로(`POST /applications/{id}/submissions`). 같은 지원 건의 문서 버전이어야 하고 미승인 `INFERRED`/`UNSUPPORTED` 블록이 있으면 `UNSUPPORTED_CLAIM_IN_EXPORT`(422). `INTERESTED`/`PREPARING`이면 `SUBMITTED`로 전이(INTERESTED는 PREPARING을 거침), 이미 `SUBMITTED`면 정정 스냅샷만 추가, 그 뒤 상태는 거부(`ApplicationStatus.acceptsSubmission`). 행은 JPA 없이 SQL insert만, 갱신·삭제 없음.
- **회고**: `ApplicationStatus.acceptsReview` 상태에서만. 첫 회고 저장이 `REVIEW_PENDING → REVIEWED`를 유발한다(`ReviewService` → `ApplicationService.advance`). 원인 단정 표현은 `Review.definitiveLanguage()`가 경고만 하고 막지 않는다. 비교 대상(`GET /applications/{id}/review-context`)은 공고·제출 스냅샷·`SubmissionFacts`·매칭 판정 등 기록된 사실만 제공하며 원인을 추론하지 않는다; UI는 이를 회고의 근거 필드에 인용만 한다.
- **요구사항**: `Requirement.manual`(사용자 입력, 즉시 APPROVED) / `Requirement.extracted`(AI, DRAFT)만으로 생성. 원문 구간은 `JobPostingSnapshot.excerpt`로 검증. 매칭·생성은 APPROVED만 사용. PATCH로 DRAFT를 만들 수 없다.
- **매칭**: 점수는 `MatchFeatureCalculator`/`MatchScore`가 결정적으로 계산하고 AI(`MatchExplainer`)는 설명·인용 구간만 붙인다 — 모델은 점수를 만들거나 바꾸지 않는다. 필수 항목 판정은 `RequirementAssessor`(REQUIRED만, 후보 없음 → UNMET). `requirement_matches`는 `application.match` 재실행 시 upsert하되 `user_decision`(ACCEPTED/REJECTED)은 절대 덮어쓰지 않고, 결정된 행은 삭제하지 않는다. 채택된 후보만 문서 생성의 근거로 쓴다.
- **문서**: `documents`(가변 식별) + `document_versions`(append-only, UPDATE 불가) + `provenance_links`. AI 초안(`document.generation` → `DocumentDrafter`)의 입력은 승인된 요구사항과 사용자가 **채택한** 매칭 후보뿐이고, 결과 블록의 참조는 컨텍스트에 대해 화이트리스트 검증, certainty는 인용 Claim 근거 상태로 상한(`GeneratedOutput.withCertaintyCeiling`), 승인 상태로 저장되지 않는다. 사용자 버전은 `GeneratedOutput.userRevision`만 통과(참조 추가·확신도 상향 불가, 손으로 쓴 블록은 UNSUPPORTED)하고 부모는 최신 버전이어야 한다(409). 블록 id는 `<section>-<n>`, 템플릿은 `DocumentTemplate`(`ko-v1`/`en-v1`, 섹션 id는 같고 문서 `language`로 고른다). 버전 비교는 `VersionDiff.compare`(블록 id 기준 추가·삭제·변경·동일 + 단어 단위 LCS)만 쓴다.
- **내보내기 (ADR-0009)**: `POST /document-versions/{id}/exports` → `document.export` 잡. 게이트는 `ExportGate.requireExportable`(API 접수 시와 worker 렌더 직전 두 번). 파일에는 제목·섹션 제목·승인된 문장만 들어가고 id·certainty·경고는 절대 쓰지 않는다(`RenderableDocument`). 렌더 후 `ExportValidator`가 텍스트를 재추출해 순서까지 검사하고 실패하면 `FAILED`. 산출물은 `export_files`(`object_key = pg:<id>`)에 저장하며 같은 버전·형식·템플릿의 READY 파일은 재사용한다. PDF는 번들한 Noto Sans KR을 서브셋 임베딩한다(`packages/document-renderer/src/main/resources/fonts`, 폰트 교체 시 `renderer_version` 상향). 다운로드는 `GET /exports/{id}/file`뿐(웹은 blob으로 받아 저장).
- **프로필**: `user_profiles`(사용자당 1개, `GET/PUT /me/profile`, 워크스페이스 소유자 기준). 내보내기 파일의 머리글(`RenderableContact`)에만 렌더링하고 문서 버전에는 저장하지 않으며 **AI 컨텍스트·로그에 절대 넣지 않는다**. 저장마다 `version`이 오르고 `exports.profile_version`과 비교해 이전 머리글로 만든 파일은 재사용하지 않는다.
- **문장 개선**: `document.revision` 잡 → `SentenceReviser`(블록 텍스트 + 인용 Claim 문장만 컨텍스트). 제안은 `RevisionGuard`(원문·Claim에 없는 수치, 빈 문장, 3배 초과 길이 → 거부)를 통과해야 하고 **저장하지 않는다** — 잡 결과로만 돌려주고 사용자가 편집기에서 비교·적용 후 버전을 저장한다(참조·certainty는 편집 규칙 그대로).
- **AI 오류 구분**: 제공자 오류는 `ModelProviderException.failure`(`ProviderFailure`)로 분류되고, worker는 `AiFailures.guard`로만 잡 오류 코드로 바꾼다(`AI_BILLING_BLOCKED`·`AI_CONFIGURATION_ERROR`는 재시도 없음 + `OPERATOR_ACTION` 로그, `AI_RATE_LIMITED`·`AI_PROVIDER_UNAVAILABLE`은 재시도). 새 AI 잡 핸들러는 직접 catch하지 말고 `AiFailures.guard`를 쓴다.
- **관측성**: api는 `RequestLogFilter`가 요청마다 한 줄(메서드·URI 템플릿·status·durationMs·requestId)을 남기고 `X-Request-Id`를 받아/만들어 응답 헤더·MDC·`audit_events.request_id`에 같은 값을 쓴다. 메트릭은 api·worker `/actuator/prometheus`(worker 기본 포트 8090)이고 잡 지표는 `JobMetrics`(`proofu.jobs.*`). **workspace id는 메트릭 라벨로 쓰지 않는다**(고카디널리티); 로그에 본문·프롬프트·토큰 금지. 스크랩 경로는 인증 없이 열려 있으므로 `MANAGEMENT_SERVER_PORT`로 내부망에만 노출한다(`docs/operations/monitoring.md`).
- **비동기 AI 작업**: 핸들러는 `WORKER_JOB_LEASE`(기본 15분) 안에 끝나야 한다 — 넘기면 `StuckJobReaper`가 워커가 죽은 것으로 보고 `LEASE_EXPIRED`로 재큐/실패시킨다(모델 호출은 4분 타임아웃 × 재시도 1회로 묶여 있다). api는 `JobService.enqueue`로 `jobs`에 넣고 202 + jobId, 웹은 `GET /jobs/{id}`를 폴링, worker의 `JobHandler`(`type` 상수는 api `JobTypes`와 동일)가 실행. AI 결과는 항상 DRAFT/미승인 상태로 저장하고 사용자가 같은 UI에서 승인한다 (예: `posting.analysis` → `Requirement.extracted`).
- **웹 보안 헤더**: 응답마다 nonce 기반 CSP(`proxy.ts`가 nonce를 만들고 `src/lib/security-headers.ts`가 정책을 만든다). 인라인 스크립트는 허용하지 않으므로 `<script>`를 직접 넣지 말고, 외부 출처를 추가하려면 정책 먼저 고친다(폰트 CDN·IdP 오리진이 현재 예외). 정적 헤더(`nosniff`, `DENY`, Referrer-Policy, Permissions-Policy, HSTS)는 `next.config.ts`에서 모든 경로에 붙인다.
- **검색**: 상단 검색창 → `/search` → `GET /api/v1/search?q=`(2자 이상). 유형별 그룹으로 최대 `limit`건 + 총 건수를 돌려주고, 부분 문자열 일치이며 기술은 별칭으로도 찾는다. 검색 대상 컬럼을 늘리면 V16 같은 trigram 인덱스도 함께 추가한다.
- **웹 패턴**: 서버 컴포넌트가 `@/lib/api`로 조회, 폼은 클라이언트 컴포넌트. 목록은 잘린 채 끝내지 않는다 — 페이지를 넘기거나(`LoadMore`/커서 링크, `PAGE_LIMIT`) 무엇까지 보여 주는지 밝힌다(`PICKER_LIMIT`, 보드의 `truncated`). 상수는 `@/lib/limits`에 두고 **클라이언트 컴포넌트에서 값을 import 하지 않는다**(서버 컴포넌트가 받으면 숫자가 아니라 client reference가 된다). 목록 조회 실패는 화면이 `ErrorState`로 직접 처리하고, 처리하지 못한 예외는 `(app)/error.tsx`(셸 붕괴 시 `global-error.tsx`)가 받는다 — 예외 메시지는 노출하지 않고 `digest`만 보여 준다. 새 목록 라우트에는 `loading.tsx`(`ListSkeleton`)를 두고 상세 라우트는 두지 않는다(`notFound()` 처리를 위해). enum 라벨은 `@/lib/labels` 조회표(알 수 없는 값은 코드 그대로). 페이지 헤더의 primary 버튼은 하나.
- **AI 출력**은 `AllowedSources` 화이트리스트로 서버 검증. `INFERRED`/`UNSUPPORTED` 블록은 사용자 승인 없이 내보내지 않는다. AI는 Evidence 검증 상태를 올릴 수 없다.
- **민감도**: `CONFIDENTIAL`은 워크스페이스 설정의 AI 동의(`workspace_settings.ai_consent`, 부여 시 재인증, 시각 기록)가 있을 때만 AI 컨텍스트에 넣고, `RESTRICTED`는 절대 넣지 않는다(`Sensitivity.allowedInAiContext(consent)`; 잡은 `WorkspaceSettingsReader.consentFor`로 읽어 `AiCall.consentToSensitive`에 전달). 새 경력·프로젝트의 `visibility` 기본값은 `workspace_settings.default_visibility`. 로그에 본문·프롬프트·토큰 금지.
- **접근성**: WCAG 2.2 AA가 기준이고 `apps/e2e/tests/accessibility.spec.ts`(axe-core)가 주요 화면·상세·폼·모바일 폭에서 **위반 0**을 강제한다. 새 화면을 추가하면 이 목록에도 넣는다. 본문 안 링크는 밑줄(색상만으로 구분 금지), 폼 오류는 `Field`가 `aria-describedby`로 연결, 앱 셸 첫 요소는 스킵 링크(`docs/design/accessibility.md`).
- **디자인**: 색상·간격은 `tokens.css` 토큰만 사용. 상태는 색상 + 텍스트/아이콘. 점수는 숫자 + 낮음/보통/높음 라벨, 합격 확률로 표현 금지. 탈락 원인은 단정하지 않는다. ATS 검사(`AtsChecker`)도 항목별 PASS/INFO/WARN 사실만 — 점수·확률 없음.
- **테스트 데이터**는 합성 데이터만 (`fixtures/`).

- **AI (ADR-0008)**: Anthropic Claude, 기본 `claude-opus-5` + 작업별 `effort`. 모든 호출은 `AiGateway.execute(workspace, AiCall)`로만 (기능 코드는 `ModelClient`를 직접 쓰지 않는다). 결과 JSON의 참조 id는 호출자가 `AiResult.context.includedSourceIds`로 화이트리스트 검증한다. 로컬 기본 `AI_PROVIDER=fake`. 실제 호출 스모크 테스트는 `ANTHROPIC_API_KEY`가 있을 때만 실행된다. 구조화 출력은 `strict` 스키마 + `GeneratedOutput.requireGrounded` 이중 검증, 추출의 원문 구간은 모델 인용문을 서버가 `RequirementExtractor.locate`로 찾아 채운다(`citations`는 구조화 출력과 병용 불가). 원문은 `document` 블록으로 격리, 프리필 금지, thinking은 adaptive 유지. 예산: 워크스페이스 월 $5, 30건/시간, 배포 일일 $50.

## 미확정 (TBD)

OIDC 제공자, 클라우드/리전, 객체 저장소(내보내기·파일 Evidence), 큐 브로커 교체 시점 → `docs/project/open-decisions.md`. 결정 시 ADR 추가 (`docs/architecture/adr/`).
