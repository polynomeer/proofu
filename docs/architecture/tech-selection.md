---
owner: 기술 책임자
status: 사후 재구성
review: 관련 ADR 갱신 시
---

# 초기 기술 선택의 대안 비교

이 문서는 2026-10-05에 작성했으며, 초기 커밋(`5d6874b`부터 `a3620d5`까지, 특히 ADR 0001–0006을 기록한 `0ffce5d`)과 저장소의 기존 문서에서 보이는 선택의 이유를 사후에 재구성한 것입니다.

ADR 0001–0006은 통합 개발 문서 v0.1 §6.5의 표를 옮긴 것이라 결정과 한 줄 이유, 재검토 조건만 있고 검토한 대안이 없습니다. 이 문서는 그 빈자리를 채웁니다. 각 절의 "필요"는 저장소 문서에 적힌 요구사항이고, "대안 비교"는 당시 기록이 아니라 그 요구사항에 비추어 다시 세운 비교입니다. 기술의 성질은 아래 참고 문헌의 공식 문서를 따랐고, 벤치마크나 측정은 하지 않았습니다. 대안이 이미 기록된 결정(ADR-0007 기술 스택, ADR-0008 AI 제공자)은 여기서 반복하지 않습니다.

## 1. 아키텍처 스타일 (ADR-0001 모듈형 모놀리스)

필요

- 경력 원천, 문서 계보(provenance), 제출 스냅샷을 한 트랜잭션 안에서 일관되게 다룹니다 ([system-overview.md](system-overview.md)).
- 리스크 표의 "초기 과설계" 가능성이 높음으로 평가되어 있습니다 ([project/risks.md](../project/risks.md)).
- 서비스 분리는 사용량과 팀 경계가 확인된 이후로 미룹니다.

대안 비교

| 선택지             | 요구사항에 비춘 장점                        | 요구사항에 비춘 단점                                                                                        |
| ------------------ | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| 마이크로서비스     | 모듈별 독립 배포와 확장                     | 원천·계보·스냅샷이 서로 다른 저장소로 나뉘면 단일 트랜잭션이 불가능해지고 경계를 잘못 그으면 이동 비용이 큼 |
| 경계 없는 모놀리스 | 가장 빠른 시작                              | 도메인 불변식이 API와 워커에 흩어지고 이후 분리 기준이 남지 않음                                            |
| 모듈형 모놀리스    | 한 DB 트랜잭션, 경계는 패키지와 모듈로 표시 | 경계를 빌드가 강제하지 않으면 시간이 지나며 흐려질 수 있음                                                  |

Martin Fowler의 "MonolithFirst"(2015)는 서비스 사이의 기능 이동이 모놀리스 안에서보다 훨씬 어렵기 때문에 경계를 먼저 모놀리스 안에서 찾으라고 권합니다. 이 저장소의 요구사항(트랜잭션 일관성, 과설계 위험)은 이 논리와 맞습니다.

결정과 비용

- 초기 커밋에서 Gradle 모듈로 분리된 것은 `packages/domain`(Spring 의존 금지)뿐이고, `apps/api` 안의 career, jobs, matching 같은 모듈은 패키지 관례입니다. 경계 위반을 잡는 정적 검사(ArchUnit 등)는 없습니다.
- API와 워커는 별도 프로세스이지만 같은 DB와 같은 도메인 모듈을 공유하므로, 워커를 독립 서비스로 떼려면 `jobs` 테이블 의존을 먼저 풀어야 합니다.

## 2. 주 저장소 (ADR-0002 PostgreSQL)

필요

- 관계 무결성: Claim↔Evidence N:N, provenance 링크, workspace 격리 ([data/erd.md](../data/erd.md)).
- 반정형 데이터: 링크 목록, 잡 payload와 결과.
- 공고 원문과 경력 서술의 전문 검색, 품질 평가 후 선택적 의미 검색 (통합 개발 문서 §7.4).
- 불변 테이블을 DB 수준에서도 막는 방어선 (ADR-0006).
- 큐를 별도 인프라 없이 시작 (ADR-0004).

대안 비교

| 선택지        | 요구사항에 비춘 장점                                                                                             | 요구사항에 비춘 단점                                                                                                        |
| ------------- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| MySQL 8.4     | 관계 무결성, CJK를 지원하는 ngram 전문 검색 파서(기본 토큰 크기 2)                                               | 의미 검색을 같은 DB에서 시작할 확장 경로가 이 저장소의 계획(pgvector)과 다름                                                |
| MongoDB       | 문서 단위 원자성, 반정형 데이터                                                                                  | 공식 문서가 다중 문서 트랜잭션보다 비정규화(임베딩)를 권장하는데, 이 도메인은 N:N 관계와 계보가 중심이라 임베딩이 맞지 않음 |
| PostgreSQL 16 | 관계 무결성, `jsonb`(인덱싱 지원), GIN 전문 검색, pgvector 확장(정확·근사 최근접 검색), 행 트리거, `SKIP LOCKED` | 한국어 형태소 분석 구성이 기본 제공되지 않음                                                                                |

결정과 비용

- 전문 검색 인덱스는 `to_tsvector('simple', ...)`입니다. `simple` 사전은 토큰을 소문자로 바꾸고 불용어만 거르므로, PostgreSQL 16에서 `to_tsvector('simple', 'Kotlin을 사용한 API 설계 경험')`은 `'kotlin을'`을 한 lexeme으로 만들고 `to_tsquery('simple', 'kotlin')`과 일치하지 않습니다(로컬 PostgreSQL 16.13에서 확인). 조사가 붙은 한국어 어절의 재현율이 낮다는 비용을 안고 시작했습니다.
- 의미 검색(pgvector)은 초기 스키마에 없습니다. 활성화는 평가 이후의 결정입니다.

## 3. API 스타일 (ADR-0003 REST와 OpenAPI)

필요

- 명시적 계약과 웹 클라이언트 타입 생성, CI의 호환성 검사 ([api/conventions.md](../api/conventions.md)).
- 오류는 RFC 9457 Problem Details 형식.
- 비동기 작업은 `202` + `jobId`와 폴링.
- 재검토 조건이 "실시간 협업이 핵심 요구가 될 때"이므로 초기에는 실시간 요구가 없습니다.

대안 비교

| 선택지             | 요구사항에 비춘 장점                                                                               | 요구사항에 비춘 단점                                                                    |
| ------------------ | -------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| GraphQL            | 클라이언트가 응답 모양을 정하고, 스키마를 인트로스펙션으로 조회                                    | HTTP 상태 코드와 Problem Details 기반 오류 규약, `202` 접수 모델과 맞추는 작업이 추가됨 |
| gRPC (gRPC-Web)    | 강한 계약과 코드 생성                                                                              | 브라우저에서 쓰려면 Envoy 같은 프록시가 필요                                            |
| REST + OpenAPI 3.1 | Schema Object가 JSON Schema 2020-12의 상위 집합, `openapi-typescript`로 런타임 의존 없는 타입 생성 | 계약 파일과 서버 구현이 따로 존재해 어긋날 수 있음                                      |

결정과 비용

- `packages/contracts/openapi.yaml`이 단일 원천이고, 서버는 springdoc으로 런타임 스펙을 노출합니다. CI의 `contract-compatibility` 잡이 둘을 비교하지만 초기에는 `|| true`로 실패를 무시하는 권고 단계였습니다(`a3620d5`의 `.github/workflows/ci.yml`).

## 4. 비동기 작업 큐 (ADR-0004 PostgreSQL job 테이블)

필요

- AI 호출과 파일 변환의 긴 실행 시간을 API 요청에서 격리합니다.
- 큐 지연 p95 2분 이하, 가장 오래된 작업 10분 초과 시 경고 ([operations/monitoring.md](../operations/monitoring.md)).
- 워커 수평 확장과 큐 기반 역압 ([non-functional.md](non-functional.md)).
- 큐 구현은 TBD였고, 환경 변수 범주에 `QUEUE_URL`과 dead letter queue가 이미 적혀 있어 전용 브로커로의 교체를 전제했습니다.

대안 비교

| 선택지                     | 요구사항에 비춘 장점                                                                              | 요구사항에 비춘 단점                                                                                              |
| -------------------------- | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Redis Streams              | 컨슈머 그룹, `XACK`, pending 목록과 `XAUTOCLAIM`으로 죽은 소비자의 메시지 회수                    | 인프라 추가, AOF `everysec` 기본 설정에서 최대 1초의 쓰기 유실 가능, 업무 데이터와 같은 트랜잭션으로 넣을 수 없음 |
| RabbitMQ                   | 수동 ack 시 채널이 닫히면 미확인 메시지를 자동 재큐                                               | 인프라 추가, 재전달을 전제로 소비자를 멱등하게 만들어야 함, DB 커밋과 발행 사이의 이중 쓰기 문제(outbox 필요)     |
| PostgreSQL + `SKIP LOCKED` | 업무 데이터와 같은 트랜잭션에서 잡 행을 삽입, 추가 인프라 없음, 여러 워커가 경합 없이 행을 가져감 | 폴링 지연(기본 2초), DB 부하가 큐 처리량과 함께 증가, 브로커가 주는 재전달·회수를 직접 구현해야 함                |

PostgreSQL 문서는 `SKIP LOCKED`가 일관되지 않은 뷰를 주므로 범용 작업에는 맞지 않지만 큐 형태 테이블에서 여러 소비자의 락 경합을 피하는 데 쓸 수 있다고 설명합니다.

결정과 비용

- `JobRepository.claim`은 `FOR UPDATE SKIP LOCKED`로 행을 고르고 `RUNNING`으로 바꾼 뒤 커밋합니다. 처리는 그 트랜잭션 밖에서 하므로, 워커가 처리 중에 죽으면 행이 `RUNNING`으로 남고 아무도 재시도하지 않습니다. 초기 설계에는 이를 회수하는 장치가 없었고, 2026-09-24에 리스와 `StuckJobReaper`가 추가되었습니다(`587d8f3`, ADR-0004 "결과와 운영 규칙").
- 재시도는 지수 백오프(5초에서 시작, 최대 300초)와 `max_attempts`(기본 3)로 직접 구현했습니다.

## 5. AI 호출 경계 (ADR-0005 제공자 중립 AI Gateway)

필요

- 민감도 `CONFIDENTIAL`/`RESTRICTED` 데이터를 허용되지 않은 AI 컨텍스트에서 제외 ([domain/evidence-model.md](../domain/evidence-model.md)).
- 출력의 JSON Schema 검증과 참조 식별자 화이트리스트 재검증 ([ai/grounding-policy.md](../ai/grounding-policy.md)).
- 호출마다 비용 기록(`ai_executions`), 원문은 로그 금지.
- 모델 교체 비용과 개인정보 정책 격리.

대안 비교

| 선택지                                         | 요구사항에 비춘 장점                                                    | 요구사항에 비춘 단점                                                                                           |
| ---------------------------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| 기능 코드가 제공자 SDK를 직접 호출             | 가장 적은 코드                                                          | 민감도 필터, 화이트리스트 검증, 비용 기록이 호출 지점마다 반복되고 하나라도 빠지면 정책 위반                   |
| 프레임워크 추상화 (예: Spring AI `ChatClient`) | 여러 제공자에 대한 공통 API, 응답의 엔티티 매핑과 스키마 검증 후 재시도 | 민감도 필터와 `AllowedSources` 화이트리스트는 도메인 규칙이라 어차피 직접 구현해야 함, 추상화 층이 하나 늘어남 |
| 자체 Gateway 한 곳                             | 정책·검증·예산·기록을 한 지점에서 강제, 기능 코드는 제공자를 모름       | Gateway 코드를 직접 유지, 제공자 고유 기능을 쓰면 중립성이 깨짐                                                |

결정과 비용

- 초기 커밋에서는 Gateway가 문서상의 결정일 뿐 코드가 없었습니다. 제공자 선택(ADR-0008)과 Gateway 모듈(`5decb6d`)은 같은 날 뒤이어 추가되었습니다.
- ADR-0008은 처음에 원문 위치를 제공자의 `citations` 기능에 의존하려 했지만, 구조화 출력과 함께 쓸 수 없어 같은 날 서버 측 인용문 위치 계산으로 정정했습니다(`47f019c`).

## 6. 제출 기록의 보존 (ADR-0006 불변 제출 스냅샷)

필요

- 과거 제출 상태를 원천 변경과 무관하게 그대로 재구성 ([product/vision.md](../product/vision.md) 재현 가능성 원칙).
- 감사 로그의 append-only 보존.
- 계정 삭제 시 파생물까지 지울 수 있어야 함 ([security/privacy-requirements.md](../security/privacy-requirements.md)).

대안 비교

| 선택지                                  | 요구사항에 비춘 장점                                                  | 요구사항에 비춘 단점                                              |
| --------------------------------------- | --------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 변경 가능한 레코드 + 감사 로그          | 구현이 단순                                                           | 제출 당시 상태를 감사 로그로부터 역산해야 하고 누락되면 재현 불가 |
| 이벤트 소싱                             | 모든 변경이 이벤트로 남아 어느 시점이든 재구성 가능                   | 모든 원천 데이터의 저장 모델이 바뀌어 MVP 범위를 크게 넘음        |
| 스냅샷 행 + 트리거로 UPDATE/DELETE 거부 | 필요한 지점(제출, 공고 원문, 상태 이벤트, provenance)만 불변으로 고정 | 계정 삭제 같은 정당한 삭제 경로를 따로 열어야 함                  |

결정과 비용

- 초기 스키마의 `reject_mutation()` 트리거는 `BEFORE UPDATE OR DELETE ... FOR EACH ROW`입니다. PostgreSQL에서 `TRUNCATE`는 행 수준 트리거를 실행하지 않으므로 이 방어선에 포함되지 않습니다.
- `provenance_links`에는 초기에 `BEFORE UPDATE` 트리거만 있었습니다.
- 계정 삭제용 전용 경로는 스키마 주석으로만 예고되었고, 이후 마이그레이션(V9)에서 구현되었습니다.

## 초기에 결정하지 않은 것

클라우드와 데이터 리전, OIDC 제공자, AI 제공자, 객체 저장소, 파일 악성코드 검사, DOCX/PDF 렌더링 라이브러리는 초기 커밋 시점에 TBD였습니다 ([project/open-decisions.md](../project/open-decisions.md)). 배포 문서는 불변 artifact 승격과 smoke test 같은 원칙만 정했고, 관측성은 신호와 SLI 목표만 정했습니다.

## 참고

- PostgreSQL 16 문서: [SELECT의 Locking Clause](https://www.postgresql.org/docs/16/sql-select.html), [텍스트 검색 인덱스](https://www.postgresql.org/docs/16/textsearch-indexes.html), [텍스트 검색 사전](https://www.postgresql.org/docs/16/textsearch-dictionaries.html), [JSON 타입](https://www.postgresql.org/docs/16/datatype-json.html), [CREATE TRIGGER](https://www.postgresql.org/docs/16/sql-createtrigger.html)
- [pgvector README](https://github.com/pgvector/pgvector)
- [MySQL 8.4 ngram Full-Text Parser](https://dev.mysql.com/doc/refman/8.4/en/fulltext-search-ngram.html)
- [MongoDB Transactions](https://www.mongodb.com/docs/manual/core/transactions/)
- [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/), [Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [OpenAPI Specification 3.1.0](https://spec.openapis.org/oas/v3.1.0.html), [openapi-typescript](https://openapi-ts.dev/introduction)
- [RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [GraphQL Specification (October 2021)](https://spec.graphql.org/October2021/), [gRPC-Web basics](https://grpc.io/docs/platforms/web/basics/)
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- Martin Fowler, [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html) (2015), [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) (2005)
