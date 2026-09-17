---
owner: 연동 책임자
status: 계약 초안 (버전 TBD)
review: 실제 제공 API 확정 시
---

# career-ops 연동 계약

- ProofU는 career-ops의 내부 데이터베이스에 직접 접근하지 않고 버전된 API 또는 이벤트 계약을 사용합니다.
- 최소 입력: `externalPostingId`, `canonicalUrl`, `company`, `roleTitle`, `description`, `publishedAt`, `capturedAt`, `contentHash`.
- 같은 `externalPostingId`와 `contentHash`는 멱등하게 처리하고 내용이 바뀌면 새 snapshot을 만듭니다.
- 수집 출처의 이용 조건과 robots 정책 준수 책임은 career-ops 경계에서 확인하되 ProofU도 출처 메타데이터를 보존합니다.
- 연동 장애 시 수동 본문 입력 경로를 유지합니다.
