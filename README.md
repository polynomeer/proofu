# ProofU

경력 사실과 증빙(Evidence)을 장기간 축적하고, 채용공고 요구사항에 맞춰 **근거를 추적할 수 있는** 이력서·자기소개서를 생성하며, 제출 스냅샷과 서류 결과 회고까지 관리하는 커리어 Source of Truth.

> 핵심 원칙: 사실 우선 · 추적 가능성 · 사용자 통제 · 재현 가능성 · 상호운용성 · 설명 가능성

## 저장소 구조

```
apps/
├─ web/        Next.js 웹 클라이언트
├─ api/        Kotlin Spring Boot REST API (/api/v1)
└─ worker/     비동기 작업 워커 (공고 분석, 문서 생성, 내보내기)
packages/
├─ domain/     순수 Kotlin 도메인 모델과 불변식 (api, worker 공유)
├─ contracts/  OpenAPI 계약과 생성된 TypeScript 타입
├─ document-renderer/  DOCX / PDF 렌더러 (예정)
└─ ai-evaluation/      AI 사실성·근거 충실도 평가 세트 (예정)
docs/          제품 · 도메인 · 아키텍처 · API · AI · 보안 · 테스트 · 운영 문서, ADR
infra/         로컬 개발용 docker compose
migrations/    Flyway SQL 마이그레이션 (단일 원천)
fixtures/      합성 테스트 데이터
scripts/       개발 보조 스크립트
```

## 시작하기

```bash
# 1. 로컬 PostgreSQL
docker compose -f infra/docker-compose.yml up -d

# 2. 웹
pnpm install
pnpm dev --filter web

# 3. API
./gradlew :api:bootRun
```

자세한 내용은 [docs/development/contribution-guide.md](docs/development/contribution-guide.md)를 참고하세요.

## 문서

- 제품 비전과 PRD: [docs/product/](docs/product/)
- 도메인 모델: [docs/domain/](docs/domain/)
- 아키텍처와 ADR: [docs/architecture/](docs/architecture/)
- 디자인 시스템: [docs/design/](docs/design/)
- 원본 기획 문서(docx): [docs/source/](docs/source/)
