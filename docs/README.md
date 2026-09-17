# ProofU 문서

원본: `docs/source/ProofU_통합_개발_문서_v0.1.docx` (2026-09-16), `docs/source/ProofU_디자인_기획서_v1.0.docx` (2026-09-16).
이 디렉터리는 원본을 영역별로 분리한 것이며, 구현과 함께 같은 릴리스로 버전 관리합니다.

## 문서 사용 원칙

- 제품 요구사항과 도메인 규칙은 코드보다 먼저 변경하고, 구현과 함께 같은 릴리스로 버전 관리합니다.
- 사용자의 경력 사실, 시스템의 추론, AI가 생성한 문장을 서로 다른 데이터로 저장합니다.
- 제출에 사용한 문서는 원천 데이터가 바뀌어도 재현할 수 있도록 불변 스냅샷으로 보존합니다.
- 확정되지 않은 항목은 TBD로 남기고 담당자와 결정 기한을 기록합니다. → [project/open-decisions.md](project/open-decisions.md)
- 개인정보와 제3자 회사 정보는 최소 수집, 목적 제한, 삭제 가능성을 기본 원칙으로 처리합니다.

## 구성

| 영역 | 문서 |
|---|---|
| 제품 | [vision](product/vision.md) · [prd](product/prd.md) · [mvp-scope](product/mvp-scope.md) · [roadmap](product/roadmap.md) · [user-journeys](product/user-journeys.md) · [glossary](product/glossary.md) |
| 도메인 | [career-data-model](domain/career-data-model.md) · [evidence-model](domain/evidence-model.md) · [application-lifecycle](domain/application-lifecycle.md) · [version-lineage](domain/version-lineage.md) |
| UX | [information-architecture](ux/information-architecture.md) · [screen-specifications](ux/screen-specifications.md) |
| 디자인 | [brand](design/brand.md) · [design-tokens](design/design-tokens.md) · [components](design/components.md) · [screens](design/screens.md) · [accessibility](design/accessibility.md) |
| 아키텍처 | [system-overview](architecture/system-overview.md) · [non-functional](architecture/non-functional.md) · [ADR](architecture/adr/) |
| 데이터 | [erd](data/erd.md) · [data-dictionary](data/data-dictionary.md) · [retention](data/retention.md) |
| API | [conventions](api/conventions.md) · [endpoints](api/endpoints.md) · [events](api/events.md) · [career-ops](api/career-ops.md) · [iterview](api/iterview.md) · OpenAPI: `packages/contracts/openapi.yaml` |
| AI | [ai-feature-spec](ai/ai-feature-spec.md) · [grounding-policy](ai/grounding-policy.md) · [evaluation](ai/evaluation.md) |
| 매칭 | [search-and-matching](matching/search-and-matching.md) |
| 문서 생성 | [document-generation](documents/document-generation.md) |
| 보안 | [threat-model](security/threat-model.md) · [privacy-requirements](security/privacy-requirements.md) |
| 테스트 | [test-strategy](testing/test-strategy.md) · [e2e-scenarios](testing/e2e-scenarios.md) |
| 운영 | [deployment](operations/deployment.md) · [monitoring](operations/monitoring.md) · [incident-response](operations/incident-response.md) |
| 개발 | [contribution-guide](development/contribution-guide.md) |
| 프로젝트 | [backlog](project/backlog.md) · [risks](project/risks.md) · [open-decisions](project/open-decisions.md) · [analytics](project/analytics.md) |
