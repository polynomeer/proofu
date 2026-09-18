---
owner: 백엔드 책임자
status: 계약 초안
review: API 릴리스 시
---

# 주요 엔드포인트

정식 계약은 `packages/contracts/openapi.yaml`입니다. ✅ 는 API 구현 완료.

| 메서드             | 경로                                        | 목적                                                    |
| ------------------ | ------------------------------------------- | ------------------------------------------------------- |
| GET                | `/dashboard` ✅                             | 대시보드 집계 (KPI, 타임라인, 최근 Evidence, 행동 필요) |
| GET, POST          | `/career-entries` ✅                        | 경력 목록 조회와 생성                                   |
| GET, POST          | `/projects` ✅                              | 프로젝트 목록(경력별 필터)과 생성                       |
| GET, PATCH, DELETE | `/projects/{id}` ✅                         | 프로젝트 상세, 변경, 삭제                               |
| GET, POST          | `/projects/{id}/achievements` ✅            | 프로젝트 성과 목록과 생성                               |
| GET, PATCH, DELETE | `/achievements/{id}` ✅                     | 성과 상세, 변경, 삭제                                   |
| GET, PATCH, DELETE | `/career-entries/{id}` ✅                   | 경력 상세, 변경, 삭제                                   |
| GET, POST          | `/evidence` ✅                              | Evidence 목록과 등록 (파일 제외)                        |
| GET, PATCH, DELETE | `/evidence/{id}` ✅                         | Evidence 상세, 변경(검증 상태 포함), 삭제               |
| GET                | `/evidence/{id}/claims` ✅                  | 이 Evidence가 연결된 Claim                              |
| GET, POST          | `/claims` ✅                                | Claim 목록(원천별)과 생성                               |
| GET, PATCH, DELETE | `/claims/{id}` ✅                           | Claim 상세(파생 상태·연결), 변경, 삭제                  |
| DELETE             | `/claims/{id}/evidence/{evidenceId}` ✅     | Claim–Evidence 연결 해제                                |
| POST ✅            | `/claims/{id}/evidence`                     | Claim과 Evidence 연결                                   |
| POST               | `/files/upload-sessions`                    | 파일 업로드 세션 생성                                   |
| GET                | `/job-postings` ✅                          | 공고 목록 (최신 스냅샷 요약)                            |
| GET, DELETE        | `/job-postings/{id}` ✅                     | 공고 상세 (스냅샷 목록), 삭제                           |
| GET                | `/job-posting-snapshots/{id}` ✅            | 불변 스냅샷 원문 조회                                   |
| POST ✅            | `/job-postings/import`                      | URL, 본문 또는 career-ops 참조 가져오기                 |
| POST               | `/job-posting-snapshots/{id}/analysis-jobs` | 공고 분석 작업 시작 (202)                               |
| GET, PATCH         | `/requirements/{id}`                        | 추출 요구사항 검토·승인                                 |
| POST               | `/applications`                             | 공고 기반 지원 생성                                     |
| POST               | `/applications/{id}/match-jobs`             | Evidence 매칭 작업 시작 (202)                           |
| POST               | `/documents/{id}/generation-jobs`           | 문서 버전 생성 (202)                                    |
| GET, POST          | `/documents/{id}/versions`                  | 버전 조회와 사용자 버전 생성                            |
| POST               | `/document-versions/{id}/exports`           | DOCX, PDF 등 내보내기 (202)                             |
| POST               | `/applications/{id}/submissions`            | 제출 스냅샷 생성                                        |
| POST               | `/applications/{id}/reviews`                | 서류 결과 회고 생성                                     |
| POST               | `/applications/{id}/interview-handoffs`     | iterview 인계 요청                                      |
| GET                | `/jobs/{id}`                                | 비동기 작업 상태와 결과 조회                            |
