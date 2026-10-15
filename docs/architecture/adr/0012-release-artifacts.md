# 0012 배포 산출물: 컨테이너 이미지와 릴리스 파이프라인

- 상태: 승인
- 일자: 2026-10-15

## 맥락

코드는 돌아가는데 **배포할 물건이 없었다**. `scripts/dev.sh`는 로컬에서 jar와 `next dev`를 띄우고, CI는 검사만 하고 끝난다. [deployment.md](../../operations/deployment.md)는 "동일한 불변 artifact를 환경별 설정만 바꾸어 승격한다"고 적어 두었지만 그 artifact가 무엇인지는 정하지 않았다.

제약:

- **클라우드와 리전이 미정**이다([open-decisions](../../project/open-decisions.md)). 쿠버네티스 매니페스트나 Terraform을 지금 쓰면 버려질 확률이 높다. 반면 **컨테이너 이미지는 어느 선택지에서도 입력**이다.
- **OIDC 제공자도 미정**이라 production 웹을 실제로 띄울 수는 없다. 그래도 이미지가 production 프로필로 **기동되는지**는 지금 증명할 수 있다 — 그 프로필은 헤더 인증·시드·가짜 AI를 끄고 없는 env에 기동 실패로 답하므로, 기동 자체가 검사다.
- api가 Flyway로 스키마를 옮기고 worker는 그 스키마를 전제한다. 순서가 배포에 드러나야 한다.
- 비밀은 이미지에 들어가지 않는다. 설정은 전부 환경 변수다.

## 선택지

| 산출물                         | 장점                                                | 단점                                                |
| ------------------------------ | --------------------------------------------------- | --------------------------------------------------- |
| **컨테이너 이미지 3개**        | 어느 런타임에도 올라감, 로컬에서 동일하게 재현 가능 | 레지스트리와 서명 체계 필요                         |
| jar + Node 번들을 직접 배포    | 이미지 빌드 없음                                    | 호스트마다 JDK·Node 버전 관리, "동일 artifact" 깨짐 |
| Buildpacks(`bootBuildImage`)   | Dockerfile 없음                                     | 웹(Next.js standalone)은 따로, 베이스 통제 어려움   |
| 클라우드별 매니페스트까지 지금 | 바로 배포 가능                                      | 클라우드 미정 — 버려질 작업                         |

JVM 베이스 이미지:

| 베이스                        | 크기(arm64, 압축 전)     | 비고                                                                          |
| ----------------------------- | ------------------------ | ----------------------------------------------------------------------------- |
| **eclipse-temurin:21-jre**    | 베이스 ~330MB, api 710MB | glibc, `curl`·셸 포함(헬스체크·디버깅)                                        |
| eclipse-temurin:21-jre-alpine | 베이스 ~190MB            | musl. 140MB 절약하지만 사용자 생성 명령이 다르고 운영 중 musl 차이를 떠안는다 |
| distroless java21             | ~230MB                   | 셸·curl 없음 → HEALTHCHECK와 장애 조사 수단 상실                              |

## 결정

1. **산출물은 컨테이너 이미지 3개**(`api`, `worker`, `web`). `infra/docker/*.Dockerfile`, 빌드 컨텍스트는 저장소 루트. 이미지에는 설정도 비밀도 없고 전부 env로 받는다.
2. **JVM 이미지는 prebuilt jar를 포장한다.** CI가 이미 `./gradlew check`로 컴파일·테스트한 jar를 쓰고(`scripts/images.sh`가 먼저 `bootJar`), 이미지 안에서 Gradle을 다시 돌리지 않는다. Spring Boot 레이어(`-Djarmode=tools extract --layers`)로 나눠 의존성 레이어는 커밋마다 바뀌지 않게 한다. 대가: Dockerfile 하나만으로는 빌드되지 않는다(스크립트나 CI가 jar를 먼저 만들어야 한다).
3. **웹 이미지는 컨테이너 안에서 빌드한다.** `next build`의 결과가 환경 독립이고, `output: "standalone"`로 추적된 의존성만 담아 294MB로 끝난다. `pnpm install --filter web...`로 `apps/e2e`(Playwright 브라우저)는 들어오지 않는다.
4. **베이스는 `eclipse-temurin:21-jre`와 `node:22-alpine`**, 둘 다 `ARG`로 바꿀 수 있게 둔다. 둘 다 **비root**(uid 10001 `proofu`, `node`)로 실행하고 `HEALTHCHECK`를 가진다(api·worker는 `/actuator/health/readiness`, 웹은 세션 없이 뜨는 `/auth/signed-out`).
5. **기동 검사가 퍼블리시의 선행 조건이다.** `scripts/images.sh --check`가 세 이미지를 `SPRING_PROFILES_ACTIVE=production`으로 띄우고(일회용 Postgres + mock IdP 컨테이너, 모델 키는 자리표시자) `scripts/smoke.sh`를 돌린다. 릴리스 워크플로는 이 검사를 통과해야 GHCR에 올린다.
6. **퍼블리시는 태그(`v*`)와 수동 실행뿐**이고 `ghcr.io/<owner>/<repo>/<image>`에 올린다. SBOM과 provenance를 attestation으로 붙이고 **cosign keyless**(워크플로 OIDC 신원)로 서명한다 — 관리할 개인 키가 없다. 지금은 `linux/amd64`만 만든다(arm64는 QEMU 비용만 들고 올릴 곳이 없다).
7. **배포 순서는 api → worker**다. worker 이미지는 api가 healthy가 된 뒤에 뜨고(`depends_on`/오케스트레이터 조건), 그래야 마이그레이션 전의 스키마를 폴링하지 않는다.
8. **배포 후 점검은 `scripts/smoke.sh`**다. 자격 증명 없이 확인할 수 있는 것만 본다: 헬스·메트릭, 익명 요청 401, API 문서·`env`·`heapdump` 차단, 세션 없는 방문자의 로그인 리다이렉트, 보안 헤더. 로그인·내보내기처럼 자격 증명이 필요한 흐름은 `scripts/e2e.sh`의 몫이다.

## 결과

- 이 결정으로 **열리는 것**: 어떤 런타임이든 "이미지를 올려라"로 끝난다. 클라우드가 정해지면 매니페스트만 추가하면 된다.
- **남는 것**: 레지스트리 접근 권한(GHCR 패키지 공개 범위), 베이스 이미지 digest 고정(현재는 태그), 멀티아키, 이미지 취약점 스캔 주기. 모두 클라우드·레지스트리 결정 뒤에 붙인다.
- 산출물 크기: api 710MB, worker 691MB(폰트 12MB 포함), web 294MB. 레이어 분리 덕에 재배포 시 바뀌는 건 수 MB다.
- `production` 프로필이 **기동 실패로 답하는 설계**가 비로소 값을 한다 — 설정이 빠진 이미지는 CI에서 뜨지 못한다.

## 재검토 조건

클라우드·오케스트레이터 확정(매니페스트·배포 순서를 그쪽으로 옮긴다), 이미지 pull 시간이 배포를 늦출 때(베이스 교체·jlink), arm64 런타임 도입, 레지스트리 변경, 서명 검증을 요구하는 정책 도입.
