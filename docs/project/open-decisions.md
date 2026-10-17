---
owner: 프로젝트 책임자
status: 실행 계획
review: 매주
---

# 출시 전 확정할 항목

| 항목                       | 현재 상태                                                                                                                                                                         | 책임             | 결정 기준                                |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ---------------------------------------- |
| 백엔드 언어와 프레임워크   | **확정** — Kotlin Spring Boot ([ADR-0007](../architecture/adr/0007-kotlin-spring-boot-and-nextjs.md))                                                                             | 기술 책임자      | 팀 숙련도, 문서 렌더링 생태계, 운영성    |
| 클라우드와 데이터 리전     | TBD — 국외 추론 허용, 한국 중심 개발. AI 추론은 당분간 `inference_geo` 미설정                                                                                                     | 기술, 보안       | 사용자 지역, 법률, 비용, 관리 서비스     |
| OIDC 제공자                | 조사 완료, 결정 대기 — 추천 Logto Cloud(JP East) + 재인증 설계 변경, 대안 Keycloak 자체 호스팅 ([ADR-0010](../architecture/adr/0010-oidc-provider-and-session-model.md) 결정 2–4) | 기술 책임자      | MFA, 계정 복구, 비용, 개인정보           |
| AI 제공자와 모델           | **확정** — Anthropic Claude, 기본 `claude-opus-5`, 작업별 effort ([ADR-0008](../architecture/adr/0008-ai-provider-anthropic-claude.md))                                           | AI, 보안         | 하위 모델 전환은 평가 세트 통과 시       |
| 파일 악성코드 검사         | TBD                                                                                                                                                                               | 보안 책임자      | 지원 형식, 처리량, 격리 모델             |
| 법적 보존 기간             | TBD                                                                                                                                                                               | 제품, 법률       | 출시 국가와 사업 주체                    |
| 가격과 사용량 제한         | AI 예산 초기값 확정: 워크스페이스 월 $5, 30건/시간, 배포 일일 $50 (ADR-0008). 요금제는 TBD                                                                                        | 제품 책임자      | AI, 변환, 저장 비용과 사용자 가치        |
| career-ops 계약 버전       | TBD                                                                                                                                                                               | 연동 책임자      | 실제 제공 API와 인증                     |
| iterview 계약 버전         | TBD                                                                                                                                                                               | 연동 책임자      | 양쪽 상태와 식별자 소유권                |
| 큐 구현                    | 초기 PostgreSQL job 테이블 ([ADR-0004](../architecture/adr/0004-async-jobs.md))                                                                                                   | 기술 책임자      | 지연·처리량                              |
| DOCX/PDF 렌더링 라이브러리 | **확정** — DOCX는 Apache POI, PDF는 OpenPDF + Noto Sans KR 번들 ([ADR-0009](../architecture/adr/0009-document-rendering-and-export-storage.md))                                   | 문서 생성 책임자 | 한글 글리프, ATS 추출 품질               |
| 내보내기·파일 저장소       | 임시 — PostgreSQL `export_files` (ADR-0009). 객체 저장소는 클라우드와 함께 결정                                                                                                   | 기술 책임자      | 용량, 서명 URL, 비용                     |
| 로고 원본 벡터             | 확정 시안 기준 SVG 수작업 정제                                                                                                                                                    | 디자인           | 개발 시작 전                             |
| 모바일 편집 범위           | 조회와 간단 수정 우선                                                                                                                                                             | 제품             | MVP 스코프 확정                          |
| iOS 앱 기술 스택           | **확정** — 네이티브 SwiftUI + 공개 OIDC 클라이언트(PKCE, 키체인), MVP 이후 ([ADR-0011](../architecture/adr/0011-ios-client-stack.md))                                             | 기술 책임자      | 디자인 충실도, 공유 확장·패스키, 팀 역량 |
| 관계 시각화                | P2로 연기                                                                                                                                                                         | 제품             | 사용자 테스트 후                         |
| AI 신뢰도 구간             | 낮음/보통/높음 3단계                                                                                                                                                              | AI               | 평가 데이터 확보 후                      |
| 트랜잭션 메일 제공자       | 제안 — 잡 + 제공자 중립 `MailSender`, 첫 어댑터 Amazon SES 서울(대안 NHN Cloud), 추적 없음, 수신 거부는 우리 DB ([ADR-0013](../architecture/adr/0013-transactional-mail.md))      | 기술, 보안       | 도달률, 비용, 개인정보                   |
| 증언 요청 상한             | 제안 — 워크스페이스 월 10건, 같은 수신자 7일 간격, 가입 후 24시간부터 ([ADR-0013](../architecture/adr/0013-transactional-mail.md) §7)                                             | 제품, 보안       | 스팸 위험과 사용자 필요                  |
| 인맥 보존 기간             | TBD — 제3자 개인정보에 별도 기간을 둘지 `trash_retention_days`를 따를지                                                                                                           | 제품, 보안       | 법률 검토와 사용자 기대                  |
| 공개 포트폴리오            | 별도 읽기 전용 템플릿                                                                                                                                                             | 제품             | MVP 이후                                 |
| 다크 모드                  | 초기 출시 제외                                                                                                                                                                    | 디자인           | 사용자 수요 확인 후                      |
