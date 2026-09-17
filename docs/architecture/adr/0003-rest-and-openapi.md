# 0003 REST와 OpenAPI

- 상태: 승인
- 일자: 2026-09-16

## 맥락

ProofU MVP 구현을 위한 초기 아키텍처 기준 (통합 개발 문서 v0.1 §6.5).

## 결정

REST와 OpenAPI

## 이유

명시적 계약과 클라이언트 타입 생성. 계약은 `packages/contracts/openapi.yaml`로 관리하고 호환성 검사를 CI에서 수행합니다.

## 재검토 조건

실시간 협업이 핵심 요구가 될 때.
