---
owner: 기술 책임자
status: 권고안
review: 중대한 기술 결정 시
---

# 시스템 아키텍처

## 결정

MVP는 도메인 경계를 내부 모듈로 분리한 **모듈형 모놀리스**입니다. 트랜잭션 일관성이 중요한 경력 원천, 문서 계보, 제출 스냅샷을 한 데이터베이스에서 관리하고, 시간이 오래 걸리는 공고 분석과 문서 생성은 작업 큐와 워커로 분리합니다. 서비스 분리는 사용량과 팀 경계가 확인된 이후 결정합니다.

## 기술 스택 ([ADR-0007](adr/0007-kotlin-spring-boot-and-nextjs.md))

| 계층         | 선택                                                           |
| ------------ | -------------------------------------------------------------- |
| Web Client   | Next.js (App Router), TypeScript, Tailwind CSS                 |
| API / Worker | Kotlin, Spring Boot 3, Gradle                                  |
| Domain       | 순수 Kotlin 모듈 (`packages/domain`) — Spring 의존 없음        |
| 데이터베이스 | PostgreSQL 16, Flyway                                          |
| 계약         | OpenAPI 3.1 (`packages/contracts`) → TypeScript 타입 생성      |
| 큐           | TBD — 초기에는 PostgreSQL 기반 job 테이블, 필요 시 전용 브로커 |

## 논리 컴포넌트

| 컴포넌트            | 책임                                   | 저장 또는 연동                    | 코드 위치               |
| ------------------- | -------------------------------------- | --------------------------------- | ----------------------- |
| Web Client          | 데이터 입력, 검토, 편집, 다운로드      | Backend API                       | `apps/web`              |
| Identity            | 로그인, 세션, 재인증, 계정             | OIDC 제공자                       | `apps/api` identity     |
| Career Module       | 경력, 프로젝트, 역량, Evidence         | PostgreSQL, Object Storage        | `apps/api` career       |
| Jobs Module         | 공고 스냅샷, 요구사항                  | career-ops, PostgreSQL            | `apps/api` jobs         |
| Matching Module     | 요구사항과 Evidence 매칭               | Search Index, Vector Index (선택) | `apps/api` matching     |
| Documents Module    | 초안, 편집, 버전, 계보, 출력           | PostgreSQL, Object Storage        | `apps/api` documents    |
| Applications Module | 지원 상태, 제출, 회고, 인계            | PostgreSQL, iterview              | `apps/api` applications |
| AI Gateway          | 모델 호출 정책, 구조화 출력, 비용 기록 | 외부 AI 제공자                    | `apps/api` ai           |
| Worker              | 분석, 생성, 변환, 재시도               | Queue                             | `apps/worker`           |
| Observability       | 로그, 메트릭, 추적, 감사               | 모니터링 플랫폼                   | 공통                    |

## 데이터 흐름

1. 클라이언트는 API를 통해 원천 데이터를 저장하고 파일은 사전 서명 업로드로 객체 저장소에 전송합니다.
2. 공고 입력 요청은 원문 스냅샷을 생성한 뒤 비동기 분석 작업을 발행합니다.
3. AI Gateway는 허용된 컨텍스트만 구성하고 구조화된 요구사항을 반환합니다.
4. Matching Module은 요구사항별 후보 Evidence를 검색하고 규칙 점수와 의미 유사도를 결합합니다.
5. 문서 생성 요청은 선택된 Evidence와 템플릿을 고정한 뒤 비동기로 생성합니다.
6. 사용자 승인 후 내보내기 워커가 DOCX와 PDF를 만들고 해시 및 보관 위치를 기록합니다.
7. 제출 시 관련 스냅샷을 불변 상태로 전환하고 이후 변경은 새 버전으로만 허용합니다.

## 저장소 구조

```
apps/web  apps/api  apps/worker
packages/domain  packages/ai-gateway  packages/contracts  packages/document-renderer  packages/ai-evaluation
docs/  infra/  migrations/  fixtures/  scripts/
```

`migrations/`가 Flyway SQL의 단일 원천이며, api 빌드 시 리소스로 복사됩니다.
