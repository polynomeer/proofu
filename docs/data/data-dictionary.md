---
owner: 백엔드와 데이터 책임자
status: 논리 설계
review: 스키마 변경 시
---

# 데이터 사전

모든 workspace 소유 테이블은 `workspace_id`를 가지며 쿼리 계층에서 격리를 강제합니다. 변경 가능한 테이블은 `revision` (원천 데이터) 또는 `version` (기타) 열로 낙관적 잠금을 적용합니다.

| 테이블                    | 핵심 열                                                                                                                               | 무결성 규칙                                                                |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| users                     | id, email, display_name                                                                                                               | OIDC subject로 식별                                                        |
| workspaces                | id, owner_user_id, name                                                                                                               | 사용자당 1개 (MVP)                                                         |
| workspace_members         | workspace_id, user_id, role                                                                                                           | (workspace, user) 유일                                                     |
| career_entries            | id, workspace_id, type, title, start_date, end_date, visibility, revision                                                             | end_date ≥ start_date                                                      |
| projects                  | id, workspace_id, career_entry_id, name, role, summary, visibility, revision                                                          | workspace 경계를 넘어 연결하지 않음                                        |
| achievements              | id, workspace_id, project_id, action, outcome, metric_value, metric_unit, confidence                                                  | 수치가 있으면 단위 필요                                                    |
| skills                    | id, workspace_id, canonical_name, category, aliases                                                                                   | (workspace, canonical_name) 유일                                           |
| project_skills            | project_id, skill_id                                                                                                                  | 중복 금지                                                                  |
| capabilities              | id, workspace_id, name, definition, level, parent_id                                                                                  | —                                                                          |
| claims                    | id, workspace_id, text, claim_type, status, sensitivity, revision                                                                     | 사실·추론·의견 구분                                                        |
| evidence                  | id, workspace_id, type, uri, object_key, body, verification, sensitivity, captured_at                                                 | NOTE→body, FILE→object_key, 그 외→uri 필요 (V2)                            |
| claim_sources             | claim_id, source_type, source_id, source_revision                                                                                     | 같은 기록 중복 금지, revision 고정                                         |
| claim_evidence            | claim_id, evidence_id, relation, scope, confidence                                                                                    | 동일 관계 중복 금지                                                        |
| capability_evidence       | capability_id, evidence_id                                                                                                            | 중복 금지                                                                  |
| job_postings              | id, workspace_id, external_posting_id, canonical_url, company, role_title                                                             | (workspace, external_posting_id) 유일                                      |
| job_posting_snapshots     | id, posting_id, source, source_url, raw_text, content_hash, captured_at                                                               | content_hash와 captured_at 보존                                            |
| requirements              | id, snapshot_id, category, text, source_span, confidence, origin, status, approved_at, deleted_at                                     | AI 추출(origin=AI)은 승인 전 DRAFT, 사용자 입력(USER)은 즉시 APPROVED (V3) |
| applications              | id, workspace_id, snapshot_id, status, company, role_title, version                                                                   | 상태 전이는 허용 목록만 사용                                               |
| application_status_events | id, application_id, from_status, to_status, occurred_at                                                                               | append only                                                                |
| documents                 | id, workspace_id, application_id, type, title                                                                                         | —                                                                          |
| document_versions         | id, document_id, parent_id, content_json, template_version, model_ref, created_by                                                     | 부모는 같은 document에 속함                                                |
| provenance_links          | version_id, block_id, source_type, source_id, source_revision, relation                                                               | 원천 revision 고정                                                         |
| submission_snapshots      | id, application_id, document_version_id, posting_snapshot_id, hash, submitted_at                                                      | 생성 후 갱신·삭제 금지                                                     |
| reviews                   | id, application_id, observed_fact, hypothesis, rationale, confidence, action, verification_plan                                       | 원인 단정 금지 (UI 경고)                                                   |
| interview_handoffs        | id, application_id, idempotency_key, handoff_id, status, consent_at                                                                   | (application, idempotency_key) 유일                                        |
| jobs                      | id, workspace_id, type, status, payload, result, attempts, scheduled_at                                                               | 비동기 작업 큐 (ADR-0004)                                                  |
| requirement_matches       | id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, reason, user_decision, run_job_id | (application, requirement, claim) 유일. 재실행 시 user_decision 보존 (V5)  |
| exports                   | id, document_version_id, format, status, object_key, sha256, size_bytes, page_count                                                   | 상태 REQUESTED→READY                                                       |
| ai_executions             | id, purpose, provider, model, prompt_version, input_hash, status, token_usage, cost                                                   | 민감 입력 원문 로그는 기본 비활성                                          |
| audit_events              | id, actor, action, target, before_hash, after_hash, occurred_at                                                                       | append only                                                                |

## 인덱스와 검색

- `(workspace_id, updated_at)` 복합 인덱스로 최근 항목 조회를 지원합니다.
- 지원 상태와 회사·포지션 조합에 부분 인덱스를 검토합니다.
- 공고 원문과 경력 서술에는 PostgreSQL 전문 검색(`tsvector`)을 기본 사용합니다.
- 의미 검색은 별도 품질 평가 후 pgvector를 선택적으로 활성화합니다.
- 민감도 필터를 검색 후처리가 아니라 검색 조건에 포함합니다.

## 마이그레이션

- 스키마 변경은 전진 호환되는 expand → migrate → contract 순서로 배포합니다.
- 프로덕션 마이그레이션은 롤백 SQL보다 데이터 보존형 보상 마이그레이션을 우선합니다.
- Flyway 파일명: `V{n}__{snake_case_description}.sql`, 위치 `migrations/`.
