---
owner: 플랫폼과 온콜 책임자
status: 운영 기준 초안
review: 릴리스 및 장애 후
---

# 배포

- local, test, staging, production을 분리하고 production 데이터와 비밀을 공유하지 않습니다.
- 빌드는 동일한 불변 artifact를 환경별 설정만 바꾸어 승격합니다.
- DB 마이그레이션은 애플리케이션 호환 버전이 배포된 뒤 실행하며 장시간 잠금을 사전 검사합니다.
- 기능 플래그로 AI 모델, 템플릿, 새 매칭 알고리즘을 단계적으로 노출합니다.
- 배포 후 로그인, 경력 조회, 공고 분석 접수, 문서 내보내기 smoke test를 실행합니다.

## production 프로필 체크리스트

`SPRING_PROFILES_ACTIVE=production`으로 api·worker를 띄우면 `application-production.yml`이 적용됩니다. 기본 프로필은 `local`이므로 **프로필을 빠뜨리면 헤더 인증·시드·가짜 AI가 켜진 채 뜹니다** — 배포 파이프라인이 반드시 설정합니다 (`ProductionProfileTest`가 아래를 검증).

| 항목     | production에서                                                                                                           | 근거                                                |
| -------- | ------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------- |
| 인증     | `HeaderWorkspaceResolver` 비활성. `WorkspaceResolver` 빈이 없으면 **기동 실패**(fail closed) — OIDC 구현이 들어와야 뜬다 | `X-Workspace-Id`는 클라이언트가 임의로 넣을 수 있음 |
| 시드     | `LocalWorkspaceSeeder`는 `local`에서만                                                                                   | 고정 UUID 워크스페이스가 생기면 안 됨               |
| DB       | `DATABASE_URL/USERNAME/PASSWORD` 필수, 기본값 없음                                                                       | `proofu/proofu`로 뜨면 안 됨                        |
| AI       | `AI_PROVIDER` 기본 `anthropic`, `fake`면 기동 실패(`AiGatewaySettings.requireRealProviderWhen`), 키 없으면 기동 실패     | 자리표시자 JSON이 요구사항·문서로 흘러감            |
| API 문서 | springdoc(`/api/v1/openapi.json`, `/api/v1/docs`) 비활성                                                                 | 계약은 저장소의 `openapi.yaml`                      |
| actuator | `health`, `info`만, `show-details: never`                                                                                |                                                     |
| 로그     | `com.proofu` info, SQL 로그 off. 본문·프롬프트·키는 어느 레벨에서도 기록하지 않음                                        | privacy-requirements                                |
| 웹       | `NEXT_PUBLIC_WORKSPACE_ID`는 로컬 전용. production 웹은 OIDC 세션이 들어오기 전까지 배포하지 않는다                      | 헤더를 브라우저가 보냄                              |

## 롤백 원칙

- 코드 롤백은 이전 불변 artifact를 재배포합니다.
- 파괴적 DB 변경과 데이터 변환은 즉시 롤백하지 않고 전진 수정과 백업 복구 조건을 따릅니다.
- AI 모델과 프롬프트는 버전별 기능 플래그로 즉시 이전 조합으로 돌릴 수 있어야 합니다.
- 롤백 후 큐의 작업이 어느 정책 버전으로 실행되는지 명확히 결정합니다.

클라우드와 데이터 리전은 TBD입니다 (→ [project/open-decisions.md](../project/open-decisions.md)).
