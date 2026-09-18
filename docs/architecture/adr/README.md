# Architecture Decision Records

| ADR                                           | 결정                                    | 상태 |
| --------------------------------------------- | --------------------------------------- | ---- |
| [0001](0001-modular-monolith.md)              | 모듈형 모놀리스                         | 승인 |
| [0002](0002-postgresql-primary-store.md)      | PostgreSQL 주 저장소                    | 승인 |
| [0003](0003-rest-and-openapi.md)              | REST와 OpenAPI                          | 승인 |
| [0004](0004-async-jobs.md)                    | 비동기 Job                              | 승인 |
| [0005](0005-provider-neutral-ai-gateway.md)   | 제공자 중립 AI Gateway                  | 승인 |
| [0006](0006-immutable-submission-snapshot.md) | 불변 제출 스냅샷                        | 승인 |
| [0007](0007-kotlin-spring-boot-and-nextjs.md) | Kotlin Spring Boot + Next.js 기술 스택  | 승인 |
| [0008](0008-ai-provider-anthropic-claude.md)  | AI 제공자와 모델 정책: Anthropic Claude | 승인 |

## 템플릿

```markdown
# NNNN 제목

- 상태: 제안 | 승인 | 폐기 | 대체됨
- 일자: YYYY-MM-DD

## 맥락

문제, 제약, 품질 요구사항

## 선택지

검토한 대안과 장단점

## 결정

선택한 방법과 적용 일자

## 결과

장점, 비용, 위험, 후속 작업

## 재검토 조건

결정을 다시 볼 수치나 사건
```
