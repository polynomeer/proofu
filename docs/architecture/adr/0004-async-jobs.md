# 0004 비동기 Job

- 상태: 승인
- 일자: 2026-09-16

## 맥락

ProofU MVP 구현을 위한 초기 아키텍처 기준 (통합 개발 문서 v0.1 §6.5).

## 결정

비동기 Job

## 이유

AI 호출과 파일 변환의 긴 실행 시간을 API 요청에서 격리합니다. 접수는 202 + jobId, 상태는 `GET /jobs/{id}`. 큐 구현은 초기에 PostgreSQL job 테이블(SKIP LOCKED)로 시작하고, 지연·처리량이 문제가 되면 전용 브로커로 교체합니다.

## 결과와 운영 규칙

- 접수는 `jobs` 행 + 202, 워커는 `SKIP LOCKED`로 집고 `attempts`를 올립니다. 실패는 지수 백오프로 재큐, `max_attempts`를 넘기면 `FAILED`.
- **리스**: 워커가 죽으면 행이 `RUNNING`으로 남아 아무도 재시도하지 않고, 사용자는 끝나지 않는 잡을 폴링하며, `JobService.enqueue`의 중복 제거가 영원히 막힙니다. `StuckJobReaper`가 `WORKER_JOB_LEASE`(기본 15분)를 넘긴 claim을 `LEASE_EXPIRED`로 되돌립니다 — 시도가 남았으면 재큐, 아니면 실패.
- 따라서 **핸들러는 리스 안에 끝나야 합니다.** 모델 호출은 `AnthropicModelClient.REQUEST_TIMEOUT`(4분) × 재시도 1회로 묶여 있고, 더 오래 걸릴 일은 잡을 나눕니다. 리스를 늘리면 사용자가 실패를 알기까지의 시간도 같이 늘어납니다.

## 재검토 조건

작업 지연이 사용자 경험을 저해할 때.
