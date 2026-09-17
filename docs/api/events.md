---
owner: 백엔드 책임자
status: 계약 초안
review: API 릴리스 시
---

# 이벤트 계약

```
CareerEntryChanged        { eventId, workspaceId, entityId, revision, changedAt }
JobPostingSnapshotCreated { snapshotId, source, contentHash, capturedAt }
DocumentVersionCreated    { documentId, versionId, provenanceCount, createdAt }
ApplicationStatusChanged  { applicationId, from, to, changedAt }
InterviewHandoffRequested { applicationId, snapshotRef, documentRefs, consentAt }
```

이벤트는 도메인 모듈의 sealed interface로 정의하고, 초기에는 프로세스 내 발행 + `audit_events` 기록으로 시작합니다.
