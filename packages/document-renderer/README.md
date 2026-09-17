# document-renderer

DOCX / PDF / Markdown 렌더러. JVM 라이브러리 선택(docx4j vs Apache POI + OpenPDF)이 확정되면 Gradle 모듈로 전환해 `apps/worker`에서 사용합니다.

품질 기준: docs/documents/document-generation.md (한글 글리프, ATS 텍스트 추출, 11pt 이상, SHA-256 기록).
