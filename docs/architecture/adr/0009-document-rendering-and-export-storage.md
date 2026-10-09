# 0009 문서 렌더링 라이브러리와 내보내기 저장

- 상태: 승인
- 일자: 2026-09-21

## 맥락

F07–F09(내보내기)는 렌더링 라이브러리와 산출물 저장소가 정해져야 시작할 수 있다. [documents/document-generation.md](../../documents/document-generation.md)의 품질 기준: 한글 글리프 누락 없음, 단일 열 ATS 친화 레이아웃, 실제 텍스트(이미지 아님), 텍스트 재추출로 필수 필드·순서 검사, SHA-256·크기·페이지 수·MIME 기록, 본문 최소 11pt, 숨은 프롬프트·내부 ID 미포함. 렌더러는 JVM(`apps/worker`)에서 실행하고 `packages/document-renderer` 모듈로 분리한다.

객체 저장소(S3 호환 등)는 클라우드와 함께 미정이다 ([open-decisions](../../project/open-decisions.md)).

## 선택지

| 선택지                          | 장점                                                                                            | 단점                                                             |
| ------------------------------- | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **Apache POI (XWPF)**           | Apache-2.0, 단일 열 문단·제목·목록에 충분, 텍스트 재추출(`XWPFWordExtractor`) 내장, 의존성 단순 | 복잡한 레이아웃·스타일 API가 저수준                              |
| docx4j                          | OOXML 전체 커버, 템플릿 바인딩                                                                  | JAXB 의존성 무겁고 Spring Boot 4/Jakarta와 조합 검증 필요        |
| PDF: OpenPDF                    | LGPL/MPL, iText 계열 API, TTF 임베딩                                                            | 한글 폰트(Noto Sans KR TTF, OFL) 번들 필요, CFF(OTF) 지원 불안정 |
| PDF: DOCX→PDF 변환(LibreOffice) | 레이아웃 일치                                                                                   | 별도 프로세스·컨테이너 이미지 필요                               |

산출물 저장:

| 선택지                                | 장점                                                          | 단점                                     |
| ------------------------------------- | ------------------------------------------------------------- | ---------------------------------------- |
| **PostgreSQL `export_files` (bytea)** | 추가 인프라 없음, 트랜잭션·백업 일원화, MVP 크기(<1MB)에 적합 | 대용량·대량에는 부적합, 나중에 이관 필요 |
| 객체 저장소                           | 확장성, 서명 URL                                              | 클라우드 미정                            |

## 결정

1. **DOCX: Apache POI XWPF** (`packages/document-renderer`, `DocxRenderer`). 단일 열, 제목 계층(문서 제목 → 섹션 제목 → 문단), 본문 11pt, 폰트는 `Malgun Gothic`/`Apple SD Gothic Neo`/`Noto Sans KR` 순 fallback을 지정한다(글리프는 열람 환경 폰트로 렌더링, 텍스트는 실제 텍스트). 내부 id·certainty·경고는 본문에 쓰지 않는다. `MARKDOWN`/`JSON`은 같은 모듈의 순수 Kotlin 렌더러.
2. **검증: 텍스트 재추출.** 렌더 후 `XWPFWordExtractor`로 텍스트를 뽑아 모든 블록 문장이 순서대로 들어 있는지 확인한다(`ExportValidator`). 실패하면 `FAILED` + `RENDER_VALIDATION_FAILED`.
3. **PDF: OpenPDF + Noto Sans KR** (`PdfRenderer`, `pdf-openpdf-1`). Google Fonts의 정적 TTF(Regular·Bold, OFL, 각 ~6MB)를 `packages/document-renderer/src/main/resources/fonts/`에 번들하고 `IDENTITY_H`로 서브셋 임베딩한다(산출물 ~60KB). 글리프가 열람 환경에 의존하지 않는 유일한 형식이므로 제출용 기본 형식으로 권한다. A4, 여백 20mm, 행간 1.5. 검증은 `PdfTextExtractor`로 재추출.
4. **저장: 당분간 PostgreSQL `export_files`**(`exports.object_key`는 `pg:<export id>`). `ExportStore` 인터페이스 뒤에 두고 객체 저장소가 정해지면 구현만 교체하고 `object_key`를 이관한다. 파일 크기 상한 5MB.
5. **재사용.** 같은 문서 버전·형식·템플릿의 `READY` 산출물이 있으면 새로 렌더링하지 않고 그 export를 돌려준다(문서 버전은 불변이므로 결과가 같다).
6. **내보내기 게이트는 도메인.** 요청 시 API가, 렌더 시 worker가 `GeneratedOutput.blocksPendingApproval()`로 미승인 블록을 거부한다(`UNSUPPORTED_CLAIM_IN_EXPORT`).

## 결과

- `renderer_version`은 렌더러 모듈 상수(`docx-poi-2`, `pdf-openpdf-3` 등)로 기록해 재현성을 남긴다. 머리글(프로필)은 `RenderableContact`로 제목 앞에 렌더링하고 `exports.profile_version`으로 고정한다.
- 폰트 12MB가 저장소와 worker 이미지에 들어간다. 폰트를 바꾸면 `renderer_version`을 올린다.
- 다운로드는 `GET /exports/{id}/file`(workspace 검증)로만, 공개 URL 없음.

## 재검토 조건

객체 저장소 확정, 파일 Evidence 도입(같은 저장소를 씀), PDF 품질 기준 실패(글리프 누락·추출 순서), 사용자 정의 템플릿 도입.
