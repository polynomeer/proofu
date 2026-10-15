# 0011 iOS 앱: 네이티브 SwiftUI 클라이언트와 공개 OIDC 클라이언트

- 상태: **승인** (2026-10-15)
- 일자: 2026-10-15

## 맥락

[mvp-scope.md](../../product/mvp-scope.md)는 모바일 네이티브 앱을 MVP에서 **제외**한다. 이 ADR은 그 이후를 위한 것으로, 베타 전에 만들자는 결정이 아니라 **만들 때 어떻게 만들지**를 정한다. 시안은 디자인 캔버스의 "iOS 앱" 줄(홈·커리어·공유 시트 Evidence 추가·지원 목록·지원 상세·문서 확인/승인)이다.

범위는 [open-decisions.md](../../project/open-decisions.md)의 "모바일 편집 범위: 조회와 간단 수정 우선"을 따른다.

| 앱에서 하는 것                                              | 웹에만 남기는 것                                  |
| ----------------------------------------------------------- | ------------------------------------------------- |
| 대시보드·타임라인·지원·문서 조회                            | 공고 붙여넣기·요구사항 분석·매칭                  |
| 공유 시트로 링크·메모 Evidence 추가, 주장에 연결            | 문서 생성·문장 편집·문장 개선·버전 비교           |
| 지원 상태 기록(`allowedTransitions`), 회고 열람             | 설정·데이터 내보내기·계정 삭제(재인증·위험 작업)  |
| 미승인 블록 승인 후 새 버전 저장, PDF 내보내기·공유(재인증) | 파일 Evidence(객체 저장소 결정 전까지 API가 막음) |

지켜야 하는 제약:

- **인증 형태가 다르다.** 웹은 BFF 세션(ADR-0010 결정 1 §1)이라 토큰이 브라우저에 없다. 앱은 BFF 없이 API를 직접 부르므로 기기가 토큰을 가져야 한다.
- **재인증은 이미 제공자 중립이다.** ADR-0010 결정 3(`POST /me/reauthentications`에 ID token)은 앱도 같은 방식으로 쓸 수 있다.
- **계약은 `packages/contracts/openapi.yaml` 하나다.** 웹은 여기서 TypeScript 타입을 만든다(`generated/api.d.ts`).
- **도메인 규칙은 서버에만 있다.** 승인·근거 상한·`allowedTransitions`는 API가 강제하고 클라이언트는 응답을 그대로 쓴다.
- **iOS 디자인 언어.** 시안은 iOS 26–27의 Liquid Glass(떠 있는 캡슐 탭 바, 유리 버튼)를 따른다. 이 재질은 시스템 표준 컴포넌트로 그릴 때만 제대로 나온다.
- **팀.** Kotlin(서버)과 TypeScript(웹) 중심이고 Swift 경험은 확인되지 않았다.

## 선택지

| 선택지                                           | 장점                                                                                                               | 단점                                                                                                                                                                        |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **A. 네이티브 SwiftUI**                          | Liquid Glass·탭 바·시트가 시스템 그대로, 공유 확장·패스키·키체인이 1급 API, OpenAPI에서 Swift 클라이언트 생성 가능 | Swift 학습, 웹과 UI 코드 공유 없음, Android는 따로                                                                                                                          |
| B. React Native(Expo)                            | TypeScript 재사용(계약 타입·일부 로직), Android 동시                                                               | Liquid Glass는 네이티브 모듈이나 근사치, 공유 확장은 결국 Swift, 새 iOS 디자인 반영이 RN 생태계 속도에 묶임                                                                 |
| C. Flutter                                       | 한 코드로 iOS·Android, 성능 양호                                                                                   | 자체 렌더러라 Liquid Glass를 흉내만 냄, 새 언어(Dart), 공유 확장은 네이티브                                                                                                 |
| D. 웹 래핑(Capacitor·WKWebView로 Next.js)        | 기존 화면 재사용, 가장 빠름                                                                                        | 네이티브 느낌·탭 바·시트 불가, BFF 쿠키 세션을 웹뷰에 맞추는 별도 처리, 공유 확장은 네이티브, App Store 심사(최소 기능) 위험                                                |
| E. Kotlin Multiplatform(공유 로직) + SwiftUI(UI) | 서버와 같은 Kotlin으로 API 클라이언트·모델 공유, UI는 A와 같음                                                     | `packages/domain`은 JVM 전용(`java.time` 등)이라 그대로 공유되지 않음. 도메인 규칙은 서버에만 두기로 했으므로 공유할 로직이 적음 — 들이는 빌드 복잡도에 비해 얻는 것이 작음 |

## 결정

**A. 네이티브 SwiftUI.** 근거: 시안의 핵심(Liquid Glass 탭 바·시트, 공유 시트로 Evidence 추가, 패스키 재인증)이 모두 Apple 표준 API에서 바로 나오고, 클라이언트에 도메인 로직이 없어 크로스플랫폼으로 공유할 것이 적다. Android가 필요해지면 그때 Android 네이티브(Compose)를 같은 원칙으로 별도 결정한다.

1. **구조.** `apps/ios`(Xcode 프로젝트, Swift Package로 모듈 분리: `ProofUAPI` 생성 클라이언트, `ProofUAuth`, 화면 모듈). 앱 타깃 + **공유 확장(Share Extension)** 타깃. 최소 지원 버전 iOS 26(Liquid Glass 표준 컴포넌트 기준, 출시 시점 재확인).
2. **API 클라이언트.** `packages/contracts/openapi.yaml`에서 Apple의 swift-openapi-generator로 생성한다. 계약이 바뀌면 웹 타입과 함께 다시 생성하고, CI가 생성물이 최신인지 확인한다. 손으로 쓴 DTO는 두지 않는다.
3. **인증: 공개 OIDC 클라이언트.** BFF가 없으므로 앱은 **별도 클라이언트**(`proofu-ios`, client secret 없음)로 등록하고 Authorization Code + PKCE(S256)를 `ASWebAuthenticationSession`으로 수행한다. redirect는 Universal Link(`https://<도메인>/auth/ios/callback`)로 받아 커스텀 스킴 탈취를 피한다.
   - access token의 `aud`는 웹과 같은 `proofu-api` — API(resource server)는 바뀌지 않는다.
   - **토큰 저장:** refresh token과 access token은 키체인(`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, 앱과 공유 확장이 쓰는 access group)에만 둔다. UserDefaults·파일에 두지 않는다. 기기 백업·이전으로 옮겨지지 않는다.
   - **세션 수명:** ADR-0010 §5를 따라 access ≤ 15분, refresh token rotation. 앱을 지우거나 로그아웃하면 키체인을 비우고 RP-initiated logout.
4. **재인증.** 민감 작업이 `REAUTHENTICATION_REQUIRED`를 받으면 `prompt=login&max_age=0`으로 다시 인가하고(패스키면 Face ID), 받은 ID token을 웹과 똑같이 `POST /me/reauthentications`로 보낸다(ADR-0010 결정 3). 이를 위해 API가 받아들이는 ID token의 `aud`를 **웹·iOS 클라이언트 목록**으로 넓힌다(`OIDC_CLIENT_ID` → 목록).
5. **데이터.** 서버가 원천이다. 앱은 화면 캐시(마지막 응답)만 두고 오프라인 쓰기는 하지 않는다 — 불변·승인 규칙이 서버에 있으므로 충돌 해결을 클라이언트에 만들지 않는다. 캐시도 민감도 `RESTRICTED` 본문은 저장하지 않는다.
6. **공유 확장.** 링크·메모만 받는다(파일은 객체 저장소 결정 후). 확장은 키체인의 토큰으로 `POST /evidence`와 Claim 연결만 호출하고, 페이지 본문은 가져오지 않는다.
7. **품질.** XCTest(생성 클라이언트·인증 흐름 단위), XCUITest로 시안의 여섯 흐름, 접근성(Dynamic Type·VoiceOver·대비 — 웹의 WCAG 2.2 AA 기준을 따름). CI는 macOS 러너에서 빌드·테스트.
8. **배포.** TestFlight 베타 → App Store. 개인정보 라벨은 웹과 같은 수집 항목(계정·경력 데이터·진단 없음)으로 작성하고, AI 처리 고지는 웹과 같은 문구를 쓴다.

## 결과

- 좋은 점: 시안 그대로의 네이티브 경험, 공유 확장·패스키·키체인을 표준 방식으로, 서버·계약은 거의 그대로(ID token `aud` 목록만 확장).
- 비용: Swift 역량 확보, macOS CI 러너 비용, Android는 별도 결정.
- 서버 쪽 후속 작업(앱 착수 전):
  - [ ] IdP에 공개 클라이언트 `proofu-ios` 등록 — 로컬 Keycloak realm에는 등록함(PKCE S256, `aud`에 `proofu-api`). 운영 제공자는 ADR-0010 결정 2 이후
  - [x] API의 `OIDC_CLIENT_ID`를 쉼표 목록으로(`proofu.auth.client-ids`, 예: `proofu-web,proofu-ios`). 재인증 ID token은 목록 중 하나가 `aud`에 있으면 받는다. 웹은 자기 id 하나만 쓴다
  - [x] `/.well-known/apple-app-site-association`(웹, `IOS_APP_IDS`=`<Team ID>.<bundle id>` 목록, 비어 있으면 404). `/auth/ios/callback`만 앱으로 넘기고, 앱이 없는 기기에는 안내 페이지를 보여 준다 — 웹은 다른 클라이언트의 코드를 교환하지 않는다
  - [ ] CI에 Swift 클라이언트 생성 최신 여부 확인 — `apps/ios`가 생길 때 함께
- 별도 ADR이 필요한 것: **푸시 알림**(마감 D-3·결과 회고 — 기기 토큰 저장, 알림 동의, APNs 발송 경로), Android.
- 위험: 공개 클라이언트는 비밀이 없으므로 PKCE·Universal Link·짧은 access token에 의존한다. refresh token이 기기에 있으므로 기기 분실 시 IdP 세션 회수 절차가 필요하다(threat-model "세션 회수" 항목과 함께).

## 재검토 조건

Android를 같은 시기에 내야 할 때(B·E 재검토), 팀에 Swift 인력이 없고 확보가 어려울 때, 모바일 범위가 "조회와 간단 수정"을 넘어 편집기 수준으로 커질 때, 선택한 IdP가 공개 클라이언트·Universal Link redirect를 지원하지 않을 때.
