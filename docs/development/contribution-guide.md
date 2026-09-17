---
owner: 기술 책임자
status: 팀 기준
review: 분기 또는 팀 변경 시
---

# 기여 가이드

## 브랜치와 리뷰

- `main`은 항상 배포 가능해야 하며 짧은 기능 브랜치와 Pull Request를 사용합니다.
- 브랜치 이름: `feat/<scope>-<short-desc>`, `fix/...`, `docs/...`, `chore/...`.
- PR은 문제, 변경 내용, 테스트, 마이그레이션, 보안 및 데이터 영향을 설명합니다.
- 도메인 불변식, 보안 경계, 마이그레이션, AI 프롬프트 변경은 최소 한 명의 책임 리뷰가 필요합니다.

## 커밋 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 를 따르며, **한 커밋은 한 가지 논리 변경 단위**입니다. 생성 파일과 비밀을 포함하지 않습니다.

```
<type>(<scope>): <subject>

<body: 무엇을, 왜>
```

| type | 용도 |
|---|---|
| `feat` | 사용자에게 보이는 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 변경 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드, 의존성, 설정 |
| `ci` | CI 워크플로우 |
| `perf` | 성능 |

scope 예: `web`, `api`, `worker`, `domain`, `contracts`, `db`, `infra`, `docs`.
subject는 영문 명령형 소문자, 마침표 없이 72자 이내. body는 한국어 또는 영어.

## Definition of Ready

- 사용자 가치와 범위가 한 문장으로 설명됩니다.
- 인수 조건과 비기능 요구사항이 있습니다.
- 데이터, 개인정보, AI, 외부 연동 영향이 식별되었습니다.
- 필요한 디자인과 API 계약이 준비되었습니다.
- 의존성과 롤아웃 또는 마이그레이션 전략이 있습니다.

## Definition of Done

- 코드 리뷰와 자동 테스트가 통과합니다.
- 사용자 스토리 인수 조건이 검증됩니다.
- 관측성, 감사 로그, 오류 처리와 접근성이 반영됩니다.
- API 스키마, 데이터 사전, ADR, 운영 문서가 변경과 함께 갱신됩니다.
- 보안 및 개인정보 체크를 완료하고 플래그 또는 롤백 경로가 준비됩니다.
- 프로덕션 또는 지정 환경에 배포하고 핵심 메트릭을 확인합니다.

## 로컬 개발

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL 16
pnpm install && pnpm dev --filter web               # http://localhost:3000
./gradlew :apps:api:bootRun                         # http://localhost:8080/api/v1
./gradlew check                                     # JVM 테스트 + lint
pnpm check                                          # web/contracts lint + typecheck
```
