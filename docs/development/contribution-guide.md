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

| type       | 용도                     |
| ---------- | ------------------------ |
| `feat`     | 사용자에게 보이는 기능   |
| `fix`      | 버그 수정                |
| `docs`     | 문서만 변경              |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test`     | 테스트 추가·수정         |
| `chore`    | 빌드, 의존성, 설정       |
| `ci`       | CI 워크플로우            |
| `perf`     | 성능                     |

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
scripts/doctor.sh    # JDK·node·pnpm·도커·포트 점검 (읽기 전용)
scripts/dev.sh       # 스택 전체: 도커(필요하면 직접 켠다)·api·worker·mock IdP·웹 dev 서버
scripts/verify.sh    # 커밋 전 관문 전부: format, enum, pnpm check, gradlew check (--e2e까지)
```

`scripts/dev.sh`는 이미 쓰고 있는 포트를 비켜 가고(5432·8080·8090·3000·8181 → 그 위 첫 빈 포트),
프로세스별 로그를 `logs/dev/`에 남기며, 실패하면 `logs/dev/failure-<시각>.log`에 설정과 로그를 모아 둡니다.
Ctrl-C는 **이 스크립트가 띄운 것만** 정리하고, 남은 것이 있으면 `scripts/stop.sh`가 치웁니다.
각각 직접 돌리려면:

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL 16
pnpm install && pnpm dev --filter web               # http://localhost:3000
./gradlew :api:bootRun                         # http://localhost:8080/api/v1
./gradlew check                                     # JVM 테스트 + lint
pnpm check                                          # web/contracts lint + typecheck
```

## AI 프롬프트 변경

`packages/ai-gateway`의 프롬프트나 `fixtures/ai`를 바꾸는 PR은 `PROMPT_VERSION`을 올리고 `ai-live` 라벨을 붙여 실모델 평가(`ai-live-eval.yml`)를 통과시킨 뒤, `docs/ai/evaluation.md` 실행 기록에 한 줄을 남깁니다. 로컬은 `scripts/ai-live-check.sh`(키는 `.env`).

## 로그인 흐름을 로컬에서 돌리기

`scripts/dev.sh`는 기본이 OIDC + mock IdP라 로그인·로그아웃이 그대로 동작하고, 로그인 없이 보려면
`scripts/dev.sh --auth header`입니다. 손으로 띄울 때는 `AUTH_MODE=header`가 기본이고, OIDC 흐름은:

```bash
node apps/web/scripts/mock-idp.mjs   # 로그인 폼 없는 가짜 IdP, http://localhost:8181/realms/mock
AUTH_MODE=oidc OIDC_ISSUER=http://localhost:8181/realms/mock \
OIDC_JWKS_URI=http://localhost:8181/realms/mock/protocol/openid-connect/certs ./gradlew :api:bootRun
AUTH_MODE=oidc OIDC_ISSUER=http://localhost:8181/realms/mock OIDC_CLIENT_ID=proofu-web OIDC_CLIENT_SECRET=x \
SESSION_SECRET=$(openssl rand -base64 32) APP_ORIGIN=http://localhost:3000 pnpm dev --filter web
```

실제 IdP 화면까지 보려면 `docker compose -f infra/docker-compose.yml --profile auth up -d`(Keycloak, `dev@proofu.local` / `devpass`, issuer `http://localhost:8180/realms/proofu`)로 바꿔 끼웁니다.

## 브라우저 여정 테스트

`scripts/e2e.sh`(Playwright, `apps/e2e`)가 실제 스택을 띄워 로그인부터 계정 삭제까지 돌립니다. 화면 문구·라벨을 바꾸면 해당 스펙의 로케이터도 함께 고칩니다. 처음 한 번 `pnpm --filter e2e exec playwright install chromium`.
