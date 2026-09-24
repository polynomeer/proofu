---
owner: 플랫폼과 온콜 책임자
status: 운영 기준 초안
review: 릴리스 및 장애 후
---

# 관측성

| 신호   | 핵심 항목                                        | 주의 사항                                            |
| ------ | ------------------------------------------------ | ---------------------------------------------------- |
| 메트릭 | 요청률, 오류율, 지연, 큐 깊이, 생성 성공률, 비용 | workspace 식별자를 고카디널리티 라벨로 사용하지 않음 |
| 로그   | requestId, jobId, action, errorCode, release     | 본문, Evidence, 프롬프트, 토큰을 기본 기록하지 않음  |
| 추적   | API, 큐, 워커, 외부 AI, 파일 변환 구간           | 민감 attribute 마스킹                                |
| 감사   | 로그인, 내보내기, 삭제, 공유, 제출, 권한 변경    | 운영 로그와 별도 보존 정책                           |

## SLI와 알림

| SLI             | 목표                 | 경고                            |
| --------------- | -------------------- | ------------------------------- |
| API 성공률      | 30일 99.5% 이상      | 5분 오류율 5% 초과              |
| 일반 API 지연   | p95 500ms 이하       | 15분 p95 1초 초과               |
| 생성 완료율     | 24시간 98% 이상      | 1시간 실패율 10% 초과           |
| 큐 지연         | p95 2분 이하         | 가장 오래된 작업 10분 초과      |
| 내보내기 완료율 | 24시간 99% 이상      | 한글 검증 또는 변환 실패 급증   |
| 비용            | 워크스페이스 예산 내 | 일간 예상치 120% 초과           |
| AI 제공자 계정  | 운영자 조치 0건      | `OPERATOR_ACTION` 로그 1건 즉시 |

`OPERATOR_ACTION` 마커(api `ApiExceptionHandler`, worker `AiFailures`)는 사람이 고쳐야 하는 제공자 오류 — 크레딧 부족(`AI_BILLING_BLOCKED`), 키·권한·요청 형식(`AI_CONFIGURATION_ERROR`) — 에만 붙습니다. 속도 제한(`AI_RATE_LIMITED`)과 장애(`AI_PROVIDER_UNAVAILABLE`)는 잡이 재시도하므로 실패율 SLI로만 봅니다.

## 구현 (현재 코드)

| 신호      | 어디서                                                                                                                                                                                                                              |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 요청 로그 | api `RequestLogFilter` — 요청마다 한 줄: `method`, URI 템플릿, `status`, `durationMs`, `requestId`. 본문·쿼리 문자열·토큰은 남기지 않는다                                                                                           |
| requestId | 클라이언트가 `X-Request-Id`를 보내면 쓰고(64자·`[A-Za-z0-9._-]`만) 아니면 생성. 응답 헤더와 MDC, `audit_events.request_id`에 같은 값이 들어간다                                                                                     |
| 메트릭    | api(기본 8080)·worker(기본 8090) `/actuator/prometheus` (Micrometer). api는 `http_server_requests`(URI 템플릿 태그), worker는 아래 잡 메트릭                                                                                        |
| 잡 메트릭 | worker `JobMetrics` — `proofu_jobs_completed_total{type,outcome}`, `proofu_jobs_duration_seconds{type}`, `proofu_jobs_queued`, `proofu_jobs_oldest_age_seconds`, `proofu_jobs_running`, `proofu_jobs_recovered_total{type,outcome}` |
| 감사      | `audit_events` (해시만, 보존 정책 별도)                                                                                                                                                                                             |

**workspace id는 메트릭 라벨로 쓰지 않습니다**(고카디널리티). 워크스페이스 단위 수치가 필요하면 `audit_events`·`ai_executions`를 질의합니다. 로그에는 워크스페이스·잡 식별자까지만 남기고 본문·프롬프트·토큰은 어느 레벨에서도 남기지 않습니다.

`proofu_jobs_oldest_age_seconds`가 큐 지연 SLI(가장 오래된 작업 10분)를, `proofu_jobs_completed_total{outcome="failed"}` 비율이 생성 완료율 SLI를 채웁니다. `proofu_jobs_recovered_total`은 워커가 죽어 리스가 만료된 잡 수입니다 — 0이 정상이고, 반복되면 워커가 재시작 중이거나 핸들러가 리스보다 오래 걸린다는 뜻입니다(ADR-0004). 추적(트레이싱)은 아직 붙이지 않았습니다.

## 스크랩 경로 보호

`/actuator/prometheus`는 인증 없이 열려 있으므로 **공개 네트워크에 노출하지 않습니다**. 배포에서는 `MANAGEMENT_SERVER_PORT`로 관리 포트를 애플리케이션 포트와 분리하고 그 포트를 내부에서만 접근하게 합니다(`docs/operations/deployment.md`).
