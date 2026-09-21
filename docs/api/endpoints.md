---
owner: 백엔드 책임자
status: 계약 초안
review: API 릴리스 시
---

# 주요 엔드포인트

정식 계약은 `packages/contracts/openapi.yaml`입니다. ✅ 는 API 구현 완료.

| 메서드                | 경로                                          | 목적                                                    |
| --------------------- | --------------------------------------------- | ------------------------------------------------------- |
| GET                   | `/dashboard` ✅                               | 대시보드 집계 (KPI, 타임라인, 최근 Evidence, 행동 필요) |
| GET, POST             | `/career-entries` ✅                          | 경력 목록 조회와 생성                                   |
| GET, POST             | `/projects` ✅                                | 프로젝트 목록(경력별 필터)과 생성                       |
| GET, PATCH, DELETE    | `/projects/{id}` ✅                           | 프로젝트 상세, 변경, 삭제                               |
| GET, POST             | `/projects/{id}/achievements` ✅              | 프로젝트 성과 목록과 생성                               |
| GET, PATCH, DELETE    | `/achievements/{id}` ✅                       | 성과 상세, 변경, 삭제                                   |
| GET, PATCH, DELETE    | `/career-entries/{id}` ✅                     | 경력 상세, 변경, 삭제                                   |
| GET, POST             | `/evidence` ✅                                | Evidence 목록과 등록 (파일 제외)                        |
| GET, PATCH, DELETE    | `/evidence/{id}` ✅                           | Evidence 상세, 변경(검증 상태 포함), 삭제               |
| GET                   | `/evidence/{id}/claims` ✅                    | 이 Evidence가 연결된 Claim                              |
| GET, POST             | `/claims` ✅                                  | Claim 목록(원천별)과 생성                               |
| GET, PATCH, DELETE    | `/claims/{id}` ✅                             | Claim 상세(파생 상태·연결), 변경, 삭제                  |
| DELETE                | `/claims/{id}/evidence/{evidenceId}` ✅       | Claim–Evidence 연결 해제                                |
| POST ✅               | `/claims/{id}/evidence`                       | Claim과 Evidence 연결                                   |
| POST                  | `/files/upload-sessions`                      | 파일 업로드 세션 생성                                   |
| GET                   | `/job-postings` ✅                            | 공고 목록 (최신 스냅샷 요약)                            |
| GET, DELETE           | `/job-postings/{id}` ✅                       | 공고 상세 (스냅샷 목록), 삭제                           |
| GET                   | `/job-posting-snapshots/{id}` ✅              | 불변 스냅샷 원문 조회                                   |
| POST ✅               | `/job-postings/import`                        | URL, 본문 또는 career-ops 참조 가져오기                 |
| POST                  | `/job-posting-snapshots/{id}/analysis-jobs`   | 공고 분석 작업 시작 (202)                               |
| GET, POST             | `/job-posting-snapshots/{id}/requirements` ✅ | 스냅샷 요구사항 목록과 수동 추가                        |
| GET, PATCH, DELETE ✅ | `/requirements/{id}`                          | 추출 요구사항 검토·승인                                 |
| GET, POST ✅          | `/applications`                               | 지원 보드 목록, 공고 스냅샷 기반 생성                   |
| GET, PATCH, DELETE    | `/applications/{id}` ✅                       | 지원 상세(상태 이력), 마감일 변경, 삭제                 |
| POST                  | `/applications/{id}/transitions` ✅           | 상태 전이 (도메인 허용 목록만)                          |
| POST                  | `/applications/{id}/match-jobs`               | Evidence 매칭 작업 시작 (202)                           |
| GET, POST ✅          | `/applications/{id}/documents`                | 지원 건의 문서 목록·생성                                |
| GET ✅                | `/documents/{id}`                             | 문서 상세(최신 버전, 템플릿 섹션)                       |
| POST ✅               | `/documents/{id}/generation-jobs`             | 문서 버전 생성 (202, `document.generation`)             |
| GET, POST ✅          | `/documents/{id}/versions`                    | 버전 조회와 사용자 버전 생성                            |
| GET ✅                | `/document-versions/{id}`                     | 버전 상세(블록, provenance)                             |
| GET, POST ✅          | `/document-versions/{id}/exports`             | 내보내기 목록·시작 (202, `document.export`; PDF는 501)  |
| GET ✅                | `/document-versions/{id}/ats-check`           | ATS 호환성 검사 결과(점수 없음)                         |
| GET ✅                | `/exports/{id}`                               | 내보내기 상태·파일 메타데이터                           |
| GET ✅                | `/exports/{id}/file`                          | READY 산출물 다운로드 (첨부)                            |
| GET, POST ✅          | `/applications/{id}/submissions`              | 제출 스냅샷 목록·생성 (불변, ADR-0006)                  |
| GET ✅                | `/submission-snapshots/{id}`                  | 제출 스냅샷 상세(고정된 문서 버전 포함)                 |
| GET, POST ✅          | `/applications/{id}/reviews`                  | 서류 결과 회고 목록과 생성                              |
| GET ✅                | `/applications/{id}/review-context`           | 회고 비교 대상(공고·제출 스냅샷·문서 사실·매칭 판정)    |
| GET, PATCH, DELETE    | `/reviews/{id}` ✅                            | 회고 상세, 변경, 삭제                                   |
| POST                  | `/applications/{id}/interview-handoffs`       | iterview 인계 요청                                      |
| GET                   | `/jobs/{id}`                                  | 비동기 작업 상태와 결과 조회                            |
