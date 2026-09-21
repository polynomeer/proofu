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
```

JDK 21 필요. `gradle` 직접 실행 시 JDK 25가 잡히면 실패하므로 항상 `./gradlew` 사용.

## 규칙

- **커밋**: Conventional Commits (`feat|fix|docs|refactor|test|chore|ci|perf(scope): subject`), 한 커밋 = 한 논리 변경. scope: `web`, `api`, `worker`, `domain`, `ai`, `renderer`, `contracts`, `db`, `infra`, `docs`. 커밋 전 해당 영역 검사 통과.
- **도메인 불변식은 `packages/domain`에 한 번만** 구현하고 단위 테스트한다. API/워커/UI는 이를 호출한다 (예: `ApplicationStatus.transitionTo`, `GeneratedOutput.requireGrounded`). DB CHECK/트리거는 방어선이지 대체가 아니다.
- **enum 값**은 domain Kotlin enum ↔ `migrations/` CHECK ↔ `openapi.yaml` ↔ `docs/domain` 네 곳이 항상 일치해야 한다. 하나를 바꾸면 넷을 바꾼다.
- **스키마 변경**은 `migrations/V{n}__*.sql` 추가만 (expand → migrate → contract). 기존 파일 수정 금지. JPA 엔티티는 스키마를 만들지 않는다.
- **불변 테이블** (`submission_snapshots`, `job_posting_snapshots`, `application_status_events`, `provenance_links`, `document_versions`, `audit_events`)은 UPDATE/DELETE 하지 않는다. 정정은 새 행.
- **API 오류**는 항상 Problem Details + `code` (`ErrorCode`). 다른 workspace 리소스는 403이 아니라 404. 컨트롤러는 `WorkspaceContext` 파라미터로 호출자 workspace를 받는다.
- **인증은 임시**: OIDC 확정 전까지 `X-Workspace-Id` 헤더 (`HeaderWorkspaceResolver`, production 프로필 제외). `local` 프로필이 시드하는 workspace `00000000-0000-7000-8000-000000000002`를 웹이 기본으로 보낸다. OIDC 도입 시 `WorkspaceResolver`만 교체.
- **API 패턴** (`apps/api/.../career` 참고): 요청 DTO는 nullable + Bean Validation(400 fieldErrors) → 도메인 객체 생성(422) → JPA 엔티티(`from`/`apply`/`toDomain`). 목록은 keyset cursor, 변경은 row lock + revision 비교(409), 모든 변경은 `AuditLog`(해시만) + 도메인 이벤트 발행.
- **Evidence/Claim 흐름**: Claim은 경력 기록(`ClaimSource`, revision 고정)에 대한 문장이고 상태(`UNSUPPORTED/SUPPORTED/CONTESTED`)는 저장하지 않고 `ClaimEvidenceLink.statusOf`로 파생한다. Evidence 위치 규칙은 유형별(NOTE→body, FILE→object_key, 그 외→절대 uri). FILE 등록은 객체 저장소 확정 전까지 API가 422로 거부한다.
- **채용공고**: `JobPosting`(가변 식별)과 `JobPostingSnapshot`(불변 원문) 분리. 스냅샷은 `JobPostingSnapshot.capture`로만 만들고 `ContentHash`(정규화 후 SHA-256)가 같으면 재사용, 다르면 새 행. 수동 붙여넣기만 구현; URL 수집·career-ops는 `NOT_IMPLEMENTED`(501).
- **지원 상태**: 전이는 `POST /applications/{id}/transitions` → `Application.transition`만. 응답의 `allowedTransitions`를 UI가 그대로 버튼으로 쓴다. `HANDED_OFF_TO_ITERVIEW`는 인계 엔드포인트 전용, `application_status_events`는 append-only.
- **제출 스냅샷**: `SubmissionSnapshot.freeze`만이 생성 경로(`POST /applications/{id}/submissions`). 같은 지원 건의 문서 버전이어야 하고 미승인 `INFERRED`/`UNSUPPORTED` 블록이 있으면 `UNSUPPORTED_CLAIM_IN_EXPORT`(422). `INTERESTED`/`PREPARING`이면 `SUBMITTED`로 전이(INTERESTED는 PREPARING을 거침), 이미 `SUBMITTED`면 정정 스냅샷만 추가, 그 뒤 상태는 거부(`ApplicationStatus.acceptsSubmission`). 행은 JPA 없이 SQL insert만, 갱신·삭제 없음.
- **회고**: `ApplicationStatus.acceptsReview` 상태에서만. 첫 회고 저장이 `REVIEW_PENDING → REVIEWED`를 유발한다(`ReviewService` → `ApplicationService.advance`). 원인 단정 표현은 `Review.definitiveLanguage()`가 경고만 하고 막지 않는다. 비교 대상(`GET /applications/{id}/review-context`)은 공고·제출 스냅샷·`SubmissionFacts`·매칭 판정 등 기록된 사실만 제공하며 원인을 추론하지 않는다; UI는 이를 회고의 근거 필드에 인용만 한다.
- **요구사항**: `Requirement.manual`(사용자 입력, 즉시 APPROVED) / `Requirement.extracted`(AI, DRAFT)만으로 생성. 원문 구간은 `JobPostingSnapshot.excerpt`로 검증. 매칭·생성은 APPROVED만 사용. PATCH로 DRAFT를 만들 수 없다.
- **매칭**: 점수는 `MatchFeatureCalculator`/`MatchScore`가 결정적으로 계산하고 AI(`MatchExplainer`)는 설명·인용 구간만 붙인다 — 모델은 점수를 만들거나 바꾸지 않는다. 필수 항목 판정은 `RequirementAssessor`(REQUIRED만, 후보 없음 → UNMET). `requirement_matches`는 `application.match` 재실행 시 upsert하되 `user_decision`(ACCEPTED/EXCLUDED)은 절대 덮어쓰지 않고, 결정된 행은 삭제하지 않는다. 채택된 후보만 문서 생성의 근거로 쓴다.
- **문서**: `documents`(가변 식별) + `document_versions`(append-only, UPDATE 불가) + `provenance_links`. AI 초안(`document.generation` → `DocumentDrafter`)의 입력은 승인된 요구사항과 사용자가 **채택한** 매칭 후보뿐이고, 결과 블록의 참조는 컨텍스트에 대해 화이트리스트 검증, certainty는 인용 Claim 근거 상태로 상한(`GeneratedOutput.withCertaintyCeiling`), 승인 상태로 저장되지 않는다. 사용자 버전은 `GeneratedOutput.userRevision`만 통과(참조 추가·확신도 상향 불가, 손으로 쓴 블록은 UNSUPPORTED)하고 부모는 최신 버전이어야 한다(409). 블록 id는 `<section>-<n>`, 템플릿은 `DocumentTemplate`(`ko-v1`).
- **내보내기 (ADR-0009)**: `POST /document-versions/{id}/exports` → `document.export` 잡. 게이트는 `ExportGate.requireExportable`(API 접수 시와 worker 렌더 직전 두 번). 파일에는 제목·섹션 제목·승인된 문장만 들어가고 id·certainty·경고는 절대 쓰지 않는다(`RenderableDocument`). 렌더 후 `ExportValidator`가 텍스트를 재추출해 순서까지 검사하고 실패하면 `FAILED`. 산출물은 `export_files`(`object_key = pg:<id>`)에 저장하며 같은 버전·형식·템플릿의 READY 파일은 재사용한다. PDF는 번들한 Noto Sans KR을 서브셋 임베딩한다(`packages/document-renderer/src/main/resources/fonts`, 폰트 교체 시 `renderer_version` 상향). 다운로드는 `GET /exports/{id}/file`뿐(웹은 blob으로 받아 저장).
- **비동기 AI 작업**: api는 `JobService.enqueue`로 `jobs`에 넣고 202 + jobId, 웹은 `GET /jobs/{id}`를 폴링, worker의 `JobHandler`(`type` 상수는 api `JobTypes`와 동일)가 실행. AI 결과는 항상 DRAFT/미승인 상태로 저장하고 사용자가 같은 UI에서 승인한다 (예: `posting.analysis` → `Requirement.extracted`).
- **웹 패턴**: 서버 컴포넌트가 `@/lib/api`로 조회, 폼은 클라이언트 컴포넌트. enum 라벨은 `@/lib/labels` 조회표(알 수 없는 값은 코드 그대로). 페이지 헤더의 primary 버튼은 하나.
- **AI 출력**은 `AllowedSources` 화이트리스트로 서버 검증. `INFERRED`/`UNSUPPORTED` 블록은 사용자 승인 없이 내보내지 않는다. AI는 Evidence 검증 상태를 올릴 수 없다.
- **민감도** `CONFIDENTIAL`/`RESTRICTED`는 동의 없이 AI 컨텍스트에 넣지 않는다. 로그에 본문·프롬프트·토큰 금지.
- **디자인**: 색상·간격은 `tokens.css` 토큰만 사용. 상태는 색상 + 텍스트/아이콘. 점수는 숫자 + 낮음/보통/높음 라벨, 합격 확률로 표현 금지. 탈락 원인은 단정하지 않는다.
- **테스트 데이터**는 합성 데이터만 (`fixtures/`).

- **AI (ADR-0008)**: Anthropic Claude, 기본 `claude-opus-5` + 작업별 `effort`. 모든 호출은 `AiGateway.execute(workspace, AiCall)`로만 (기능 코드는 `ModelClient`를 직접 쓰지 않는다). 결과 JSON의 참조 id는 호출자가 `AiResult.context.includedSourceIds`로 화이트리스트 검증한다. 로컬 기본 `AI_PROVIDER=fake`. 실제 호출 스모크 테스트는 `ANTHROPIC_API_KEY`가 있을 때만 실행된다. 구조화 출력은 `strict` 스키마 + `GeneratedOutput.requireGrounded` 이중 검증, 추출의 원문 구간은 모델 인용문을 서버가 `RequirementExtractor.locate`로 찾아 채운다(`citations`는 구조화 출력과 병용 불가). 원문은 `document` 블록으로 격리, 프리필 금지, thinking은 adaptive 유지. 예산: 워크스페이스 월 $5, 30건/시간, 배포 일일 $50.

## 미확정 (TBD)

OIDC 제공자, 클라우드/리전, 객체 저장소(내보내기·파일 Evidence), 큐 브로커 교체 시점 → `docs/project/open-decisions.md`. 결정 시 ADR 추가 (`docs/architecture/adr/`).
