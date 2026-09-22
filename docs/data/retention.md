---
owner: 백엔드와 데이터 책임자
status: 논리 설계
review: 법률 검토 후 확정
---

# 보존과 삭제

| 데이터                | 기본 보존                | 삭제 처리                                                                              |
| --------------------- | ------------------------ | -------------------------------------------------------------------------------------- |
| 활성 계정 원천 데이터 | 계정 유지 기간           | 사용자 삭제 또는 계정 삭제 시 제거 큐에 등록                                           |
| 휴지통 데이터         | 설정값(기본 30일, 7–365) | 기간 후 정리 잡이 영구 삭제 (아래 "보존 기간 설정")                                    |
| 제출 스냅샷           | 계정 유지 기간           | 사용자 요청 시 관련 파생물과 함께 삭제                                                 |
| 감사 로그             | 1년 권고                 | 개인 식별 최소화 후 정책에 따라 만료                                                   |
| 내보내기 파일         | 설정값(기본 7일, 1–90)   | 문서 파일·전체 ZIP 모두 만료 시 파일 삭제, 행은 `EXPIRED`                              |
| AI 실행 메타데이터    | 90일 권고                | 입력 원문은 기본 저장하지 않음. 문서 버전·매칭 행이 참조하므로 자동 삭제는 미구현(TBD) |
| 백업                  | 35일 권고                | 만료 시 자동 파기하며 삭제 반영 한계를 고지                                            |

## 보존 기간 설정과 정리 (`RetentionSweeper`)

`workspace_settings.trash_retention_days`(7–365, 기본 30)와 `export_retention_days`(1–90, 기본 7)를 `PUT /me/settings`의 `retention`으로 정합니다(`RetentionPolicy`). 워커의 `RetentionSweeper`가 한 시간마다(`WORKER_RETENTION_INTERVAL`) 삭제되지 않은 workspace마다 정책을 읽어 정리합니다. 잡 테이블을 거치지 않는 시스템 작업이며 결과는 로그(표별 건수)와 `audit_events`(actor `SYSTEM`, `retention.swept`)에 남습니다.

**휴지통 정리** — `deleted_at`이 보존 기간보다 오래된 **원천 데이터**만 영구 삭제합니다: 성과 → 프로젝트(`project_skills` 포함) → 경력 → 스킬 → 역량(`capability_evidence` 포함) → Claim(`claim_sources`·`claim_evidence`·그 Claim의 `requirement_matches` 포함) → Evidence(`claim_evidence`·`capability_evidence` 포함) → 요구사항(그 요구사항의 `requirement_matches` 포함). 살아 있는 행이 아직 참조하는 원천(예: 삭제되지 않은 Claim이 `claim_sources`로 가리키는 프로젝트, 삭제되지 않은 성과가 있는 프로젝트, 하위가 있는 역량, 살아 있는 프로젝트에 붙은 스킬)은 참조가 사라질 때까지 휴지통에 남습니다. `provenance_links`는 불변이므로 정리된 Claim·Evidence·요구사항을 계속 가리킬 수 있고, 화면은 이를 "삭제된 근거"로 보여줍니다.

**정리하지 않는 것** — 공고·지원·문서는 불변 자식(공고 스냅샷, 상태 이벤트, 문서 버전, 제출 스냅샷)을 가지므로 휴지통에서도 영구 삭제하지 않고 계정 삭제 때만 제거합니다(위 표 "제출 스냅샷"). 감사 로그와 AI 실행 메타데이터도 정리 잡 범위 밖입니다.

**내보내기 만료** — `exports`는 `created_at`, `account_exports`는 `expires_at`(만들 때 `export_retention_days`로 계산) 기준으로 `READY → EXPIRED`가 되고 `export_files`의 바이트를 지웁니다(같은 파일을 가리키는 READY 행이 없을 때). 만료된 문서 내보내기는 다시 요청하면 새로 렌더하고, 전체 내보내기는 새 요청으로만 받습니다.

## 전체 데이터 내보내기 (`account.export`)

`POST /me/exports`가 접수하면 잡이 workspace의 모든 테이블 행을 `data.json`으로, READY 내보내기 파일을 `exports/`로 묶은 ZIP을 만들어 `export_files`에 저장합니다(`account_exports` 행, `tables`에 표별 행 수). 다운로드는 `GET /me/exports/{id}/file`로만, **최근 재인증**이 필요하고 `export_retention_days`(기본 7일) 뒤 만료됩니다(만료 후 409, 파일은 `RetentionSweeper`가 지움). 프로필·설정·감사 기록도 포함합니다.

## 계정 삭제 (`account.purge`)

1. `DELETE /me`는 최근 재인증(`REAUTHENTICATION_REQUIRED`)을 요구하고, 즉시 `users.deleted_at`·`workspaces.deleted_at`을 기록해 로그인을 막은 뒤 `account.purge` 잡을 접수합니다(202). 웹은 곧바로 로그아웃합니다.
2. 잡은 한 트랜잭션에서 `SET LOCAL proofu.purge = 'on'`으로 불변 트리거의 DELETE만 허용받아 workspace의 모든 행을 지웁니다: 내보내기 파일·exports, provenance, 제출 스냅샷, 문서 버전·문서, 회고, 인계, 매칭, 상태 이벤트, 지원, 요구사항, 공고 스냅샷·공고, Claim/Evidence 연결, Claim, Evidence, 성과·프로젝트·스킬·역량·경력, AI 실행 기록, 다른 잡, 전체 내보내기(ZIP 포함), 프로필, 멤버십.
3. `users`와 `workspaces` 행은 **묘비**로 남습니다: 이메일·이름·OIDC subject를 `deleted:<id>` 형태로 덮어써 개인정보를 제거하고, 같은 subject가 다시 가입하면 새 사용자로 만들어집니다. `audit_events`는 개인정보 없이(id·해시만) 보존 정책(1년)을 따릅니다.
4. 제공자 측 사용자 삭제는 제공자 확정 후 어댑터로 붙입니다(ADR-0010 결정 2 기준). 그때까지 잡 결과에 `providerUserDeleted=false`를 남깁니다.
5. 백업에는 삭제가 35일 뒤에 반영됩니다(위 표).

## 백업

- 일일 전체 백업과 지속적 트랜잭션 로그 보관을 구성하고 암호화합니다.
- 분기마다 별도 환경에서 복구하여 RPO/RTO와 파일 객체 참조 무결성을 검증합니다.
