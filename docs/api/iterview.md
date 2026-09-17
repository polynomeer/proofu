---
owner: 연동 책임자
status: 계약 초안 (버전 TBD)
review: 양쪽 상태와 식별자 소유권 확정 시
---

# iterview 연동 계약

- 인계는 Application이 `DOCUMENT_PASSED`이고 사용자가 명시적으로 승인했을 때만 가능합니다.
- 최소 전송 데이터: `applicationId`, `company`, `roleTitle`, `postingSnapshotRef`, `submittedDocumentRefs`, 사용자 선택 메모.
- 기밀 Evidence 원문과 불필요한 개인정보는 전송하지 않습니다.
- iterview가 반환한 `handoffId`를 저장하고 이후 면접 상태의 Source of Truth는 iterview로 둡니다.
- 재시도는 동일 `idempotencyKey`를 사용하고 중복 면접 건 생성을 방지합니다.
