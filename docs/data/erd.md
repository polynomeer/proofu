---
owner: 백엔드와 데이터 책임자
status: 논리 설계
review: 스키마 변경 시
---

# 엔터티 관계

```
User 1---N WorkspaceMember N---1 Workspace
Workspace 1---N CareerEntry 1---N Project 1---N Achievement
Claim N---N Evidence
Capability N---N Evidence
Skill N---N Project
JobPosting 1---N JobPostingSnapshot 1---N Requirement
Application N---1 JobPostingSnapshot
Application 1---N Document 1---N DocumentVersion
DocumentVersion N---N SourceRevision  (through ProvenanceLink)
Application 1---N SubmissionSnapshot
Application 1---N Review
Application 1---N ApplicationStatusEvent
```

```mermaid
erDiagram
  users ||--o{ workspace_members : has
  workspaces ||--o{ workspace_members : has
  workspaces ||--o{ career_entries : owns
  career_entries ||--o{ projects : contains
  projects ||--o{ achievements : contains
  projects }o--o{ skills : uses
  workspaces ||--o{ claims : owns
  workspaces ||--o{ evidence : owns
  claims }o--o{ evidence : claim_evidence
  capabilities }o--o{ evidence : capability_evidence
  job_postings ||--o{ job_posting_snapshots : versions
  job_posting_snapshots ||--o{ requirements : extracts
  job_posting_snapshots ||--o{ applications : targets
  applications ||--o{ documents : has
  documents ||--o{ document_versions : has
  document_versions ||--o{ provenance_links : cites
  applications ||--o{ submission_snapshots : freezes
  applications ||--o{ reviews : reflects
  applications ||--o{ application_status_events : logs
```

물리 스키마: `migrations/V1__initial_schema.sql`. 데이터 사전: [data-dictionary.md](data-dictionary.md).
