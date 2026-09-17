---
owner: 프로젝트 책임자
status: 실행 계획
review: 매주
---

# 초기 백로그

| 순위 | 작업 | 산출물 | 완료 기준 |
|---|---|---|---|
| P0 | 도메인 스키마와 마이그레이션 | ERD, migration, fixture | 핵심 불변식 단위 테스트 |
| P0 | 인증과 workspace 격리 | OIDC middleware, policy | 교차 사용자 접근 테스트 |
| P0 | Career Entry, Project, Achievement API | OpenAPI와 CRUD | 검색, 수정, 충돌 처리 |
| P0 | Evidence 파일과 Claim 연결 | 업로드 검증, 관계 API | 민감도와 삭제 전파 |
| P0 | 공고 snapshot과 Requirement | 가져오기, 분석, 승인 UI | 원문 위치와 hash 보존 |
| P0 | AI Gateway | 정책, 구조화 출력, 실행 로그 | 화이트리스트 참조 검증 |
| P0 | 문서 version과 provenance | 버전 그래프와 비교 | 원천 revision 재현 |
| P0 | DOCX/PDF 내보내기 | renderer, validator | 한글 및 ATS 검사 |
| P1 | 적합도 설명 | 점수 분해, Evidence 목록 | 평가 세트 기준 통과 |
| P1 | 지원 상태와 제출 snapshot | 상태 이벤트, 불변 snapshot | 상태 전이 테스트 |
| P1 | 서류 회고 | 사실, 가설, 행동 폼 | 원인 단정 경고 |
| P1 | career-ops 연동 | adapter, 계약 테스트 | 중복과 변경 처리 |
| P1 | iterview 인계 | 동의와 멱등 인계 | 중복 생성 방지 |
| P2 | 기존 이력서 가져오기 | parser, review flow | 원문과 추출 결과 비교 |
| P2 | 의미 검색 | embedding index, evaluator | 키워드 대비 품질 개선 증명 |
