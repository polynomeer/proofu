# 0007 Kotlin Spring Boot + Next.js 기술 스택

- 상태: 승인
- 일자: 2026-09-18

## 맥락

통합 개발 문서 v0.1 부록 B에서 백엔드 구현 언어와 프레임워크는 TBD였습니다. 결정 기준은 팀 숙련도, 문서 렌더링 생태계, 운영성입니다.

## 선택지

| 선택지 | 장점 | 단점 |
|---|---|---|
| TypeScript 풀스택 (Next.js + NestJS) | 단일 언어, docx/pdf JS 생태계 | 대규모 도메인 로직의 타입 안정성·운영 성숙도 |
| Next.js + Python FastAPI | AI 도구 생태계 | 두 언어 유지, 문서 렌더링 생태계 약함 |
| Next.js + Kotlin Spring Boot | 엔터프라이즈 운영성, 강한 타입, JVM 문서 생태계(docx4j, Apache POI, OpenPDF) | 초기 세팅 비용, 두 언어 유지 |

## 결정

- **웹**: Next.js (App Router) + TypeScript + Tailwind CSS
- **API / Worker**: Kotlin + Spring Boot 3 + Gradle (Kotlin DSL), JDK 21
- **도메인**: Spring 의존이 없는 순수 Kotlin 모듈 `packages/domain` — 불변식과 상태 전이를 여기서 단위 테스트
- **DB**: PostgreSQL 16 + Flyway (SQL 마이그레이션은 `migrations/`가 단일 원천)
- **계약**: OpenAPI 3.1 → `openapi-typescript`로 웹 타입 생성. 서버는 springdoc으로 런타임 스펙을 노출하고 CI에서 계약 파일과 비교

## 결과

- 도메인 불변식이 API/Worker에 중복되지 않고 한 모듈에 모입니다.
- DOCX/PDF 렌더러는 JVM 라이브러리로 워커에서 구현합니다 (`packages/document-renderer`는 Gradle 모듈로 전환 예정).
- 프론트엔드와 백엔드 사이의 계약 드리프트는 OpenAPI 계약 검사로 막습니다.

## 재검토 조건

- 문서 렌더링 품질(한글 글리프, ATS 추출)이 JVM 생태계에서 기준을 만족하지 못할 때
- 팀 구성 변화로 JVM 운영 역량이 부족해질 때
