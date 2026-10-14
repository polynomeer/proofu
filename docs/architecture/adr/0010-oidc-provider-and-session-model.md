# 0010 인증: OIDC 연동 형태와 제공자

- 상태: **제안** (제공자 선택은 결정 대기, 연동 형태는 결정 제안)
- 일자: 2026-09-21 (개정 2026-10-14: 제공자 조사 결과, 결정 3·4 제안)

## 맥락

인증은 `X-Workspace-Id` 헤더로 대신하고 있고(`HeaderWorkspaceResolver`, `production`에서 비활성), `WorkspaceResolver` 빈이 없으면 production이 기동하지 않는다([deployment.md](../../operations/deployment.md)). OIDC 제공자가 **production 기동의 유일한 차단 요소**다.

문서가 요구하는 것:

| 요구                                                                                              | 출처                                                              |
| ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 표준 OIDC, 비밀번호를 직접 저장하지 않음                                                          | [threat-model.md](../../security/threat-model.md) §인증과 권한    |
| MFA 지원, 세션 회수, 이상 탐지                                                                    | threat-model §계정 탈취                                           |
| 민감 작업(전체 내보내기·계정 삭제·연동 변경)에 **최근 재인증** — `REAUTHENTICATION_REQUIRED`(401) | threat-model, [conventions.md](../../api/conventions.md)          |
| 설정 화면의 재인증 대기/완료/실패 상태                                                            | [screen-specifications.md](../../ux/screen-specifications.md) S01 |
| 계정 삭제 = 활성 데이터·산출물·인덱스·저장소 전부 삭제                                            | [privacy-requirements.md](../../security/privacy-requirements.md) |
| env: `OIDC_ISSUER`, `OIDC_CLIENT_ID`, redirect URI 허용 목록                                      | [non-functional.md](../../architecture/non-functional.md)         |
| 결정 기준: MFA, 계정 복구, 비용, 개인정보                                                         | [open-decisions.md](../../project/open-decisions.md)              |

확인된 전제: 한국 사용자 중심, 클라우드 미정(→ 특정 클라우드에 묶이는 IdP는 불리), 사용자당 workspace 1개(MVP), 팀·조직 기능 없음. 스키마는 이미 `users(oidc_issuer, oidc_subject)`로 제공자 중립이다.

## 결정 1 (제안): 연동 형태는 제공자와 무관하게 지금 정한다

어떤 제공자를 고르든 아래 형태는 같다. 이 부분은 제공자 결정을 기다리지 않고 구현할 수 있다(로컬은 docker-compose의 Keycloak 같은 표준 IdP로 개발).

1. **Authorization Code + PKCE, BFF 세션.** 브라우저는 토큰을 갖지 않는다. `apps/web`(Next.js 서버)이 confidential client로 코드를 교환하고, **httpOnly·Secure·SameSite=Lax 세션 쿠키**로 사용자를 식별하며, 서버 컴포넌트·Route Handler가 API를 호출할 때 세션에 보관한 access token을 `Authorization: Bearer`로 붙인다. 지금의 `X-Workspace-Id` 미들웨어를 이 헤더 주입으로 교체한다(`apps/web/src/lib/api.ts`의 미들웨어 한 곳).
2. **API는 OIDC resource server.** Spring Security OAuth2 Resource Server(JWT)로 `OIDC_ISSUER`의 JWKS로 서명을 검증하고, `OidcWorkspaceResolver`가 `(iss, sub)`로 `users`를 찾아(없으면 첫 로그인 시 생성 + workspace 1개 + `workspace_members OWNER`) `WorkspaceContext`를 만든다. 컨트롤러·서비스는 바뀌지 않는다 — `WorkspaceResolver` 교체가 전부라는 기존 설계가 그대로 성립한다.
3. **worker는 인증 없음.** 잡은 `workspace_id`를 이미 갖고 있고 외부에서 호출되지 않는다.
4. **재인증(step-up).** 민감 엔드포인트는 토큰의 `auth_time`이 N분(초기값 10분) 이내여야 하고, 아니면 `REAUTHENTICATION_REQUIRED`. 웹은 이 코드를 받으면 `prompt=login`(또는 제공자의 `max_age`)으로 다시 인가 요청을 보낸 뒤 원래 작업으로 돌아온다. 대상: 전체 내보내기, 계정 삭제, 연동 변경, 프로필 이메일 변경.
5. **세션 수명.** 웹 세션 24시간 절대 만료·2시간 유휴 만료, access token은 짧게(≤ 15분) refresh token으로 갱신, refresh token은 서버 세션에만 저장. 로그아웃은 로컬 세션 폐기 + RP-initiated logout.
6. **계정 삭제.** 삭제 잡(`account.purge`)이 우리 데이터를 지운 뒤 제공자 사용자도 API로 삭제한다(제공자가 지원해야 함 — 선택 기준).
7. **이메일 검증·MFA·복구는 제공자에 위임.** 우리는 `email_verified=false`인 사용자를 로그인시키지 않는다.
8. **감사.** 로그인·로그아웃·재인증·삭제를 `audit_events`에 남긴다(토큰·이메일 본문 없이 해시).

## 결정 2 (대기): 제공자

### 기준

| 기준                                                                                                | 왜                             | 가중 |
| --------------------------------------------------------------------------------------------------- | ------------------------------ | ---- |
| 표준 준수: OIDC Discovery, PKCE, `auth_time`/`max_age`, RP-initiated logout, 관리 API로 사용자 삭제 | 결정 1이 그대로 성립해야 함    | 필수 |
| MFA(TOTP·패스키) 기본 제공                                                                          | threat-model                   | 필수 |
| 한국 사용자 소셜 로그인: **카카오·네이버**(+ Google·Apple)                                          | 한국 중심                      | 높음 |
| 클라우드 중립(특정 클라우드 계정 불필요)                                                            | 클라우드 미정                  | 높음 |
| 개인정보: 데이터 저장 위치를 선택·명시할 수 있고 처리 계약(DPA) 제공                                | privacy-requirements 고지 의무 | 높음 |
| 비용: MAU 1만 이하 구간에서 예측 가능                                                               | 요금제 TBD                     | 중간 |
| 운영 부담: 패치·백업·가용성을 우리가 지지 않아도 되는지                                             | 소규모 팀                      | 중간 |
| 자체 호스팅 탈출구: 벤더 종속 시 이전 가능한지                                                      | 장기                           | 낮음 |

### 선택지

| 선택지                                                                 | 장점                                                              | 단점 / 확인할 것                                                                                                                      |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **A. 관리형 OIDC 서비스 (예: Auth0/Okta CIC)**                         | 표준 준수·MFA·관리 API 성숙, 클라우드 중립, 소셜 커넥션 확장 가능 | 카카오·네이버는 커스텀 소셜 커넥션으로 직접 구성, 비용 구간과 데이터 리전(아시아 리전 여부) 확인 필요                                 |
| **B. 오픈소스 IdP의 관리형 클라우드 (예: Logto Cloud, Zitadel Cloud)** | 카카오·네이버 커넥터 내장(Logto), 자체 호스팅 탈출구, 비용 낮음   | 상대적으로 신생 — SLA·DPA·리전 확인 필요, 기능 성숙도(step-up, 사용자 삭제 API) 검증 필요                                             |
| **C. 자체 호스팅 Keycloak**                                            | 완전 통제, 비용 = 인프라뿐, 표준 준수 우수                        | 우리가 운영(패치·백업·가용성), 카카오·네이버는 확장 플러그인, MVP 팀 규모에 과함. **로컬 개발용 IdP로는 채택**                        |
| D. 클라우드 IdP (AWS Cognito, Azure AD B2C, Firebase Auth)             | 해당 클라우드를 쓸 때 저렴·통합                                   | 클라우드가 미정이라 지금 고르면 클라우드 결정을 선점, 카카오·네이버 미지원 또는 우회 필요, 일부는 표준 기능(logout, `auth_time`) 제약 |
| E. 소셜 로그인만 직접 (카카오/네이버/Google OAuth를 앱이 직접 처리)    | 제공자 비용 0                                                     | 우리가 세션·MFA·복구·계정 연결을 직접 만들어야 함 — threat-model "비밀번호 미저장·MFA 위임"과 어긋남                                  |

### 조사 결과 (2026-10-14, 공식 문서·가격표·공개 discovery 기준)

각 칸은 공식 출처로 확인한 것만 적었다. "코드 기준"은 문서에 없고 공개 소스에서만 확인한 동작이라 테스트 테넌트로 다시 확인해야 한다.

| 기준                                     | Logto Cloud                                                                             | Auth0 (Okta CIC)                                                                          | Zitadel Cloud                                     | Keycloak 자체 호스팅                 |
| ---------------------------------------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------- | ------------------------------------ |
| PKCE S256·`end_session_endpoint`         | 예                                                                                      | 예 (2023-11-14 이전 테넌트는 켜야 함)                                                     | 예                                                | 예                                   |
| **access token의 `auth_time`**           | **아니오** — 코드는 있으나 개발 기능 플래그 뒤(미출시)                                  | **아니오** — 예약 클레임이라 직접 넣을 수 없고, 로그인 시각을 읽는 수단은 Enterprise 전용 | **아니오** — 클레임 표에 ID token만               | **예** (v25부터 `basic` 스코프 기본) |
| `prompt=login`/`max_age=0` 재인증        | 예 (`max_age=0` → `prompt=login`, 코드 기준)                                            | 예, ID token에 `auth_time` 포함. 소셜 IdP에서는 재인증을 강제 못 함                       | `prompt=login` 예, `max_age` 동작은 문서 없음     | 예                                   |
| 사용자 삭제 API                          | 예 (`DELETE /api/users/{id}`, M2M)                                                      | 예 (`DELETE /api/v2/users/{id}`, M2M)                                                     | 예, 단 "deleted" 상태 전환 — 완전 삭제인지 미확인 | 예 (Admin REST)                      |
| 카카오·네이버                            | **둘 다 내장 커넥터**                                                                   | 카카오: 파트너 마켓플레이스(스코프 오류 보고), 네이버: 없음 → 커스텀                      | 내장 없음 → 일반 OIDC(카카오)·OAuth(네이버)       | 내장 일반 OIDC·OAuth로 구성 가능     |
| MFA(TOTP·패스키) 셀프 등록               | 예 — **유료 애드온 $48/월**                                                             | 예 — Essentials 이상                                                                      | 예                                                | 예                                   |
| 리전                                     | EU·US West·AU·**JP East** (생성 시 고정, 한국 없음)                                     | AU·CA·EU·**JP**·UK·US (한국 없음)                                                         | US·EU·CH·AU (아시아 없음)                         | 우리가 정함 (국내 가능)              |
| DPA·인증                                 | DPA, SOC 2 Type II                                                                      | DPA, ISO 27001·SOC 2                                                                      | DPA, ISO 27001·SOC 2                              | 해당 없음(우리 책임)                 |
| 월 비용 (커스텀 도메인 + MFA + 관리 API) | 약 **$72** (Pro $24 + MFA $48), MAU 과금 없음, 액세스 토큰 5만 개 초과 시 100개당 $0.08 | 1천 MAU 약 **$70**, 1만 MAU 약 **$700** (Essentials)                                      | **$100** (Pro, DAU 기준, 초과 단가 미공개)        | 인프라·운영 인건비                   |
| SLA                                      | Enterprise만                                                                            | Enterprise만 (99.99%)                                                                     | Pro 99.5% 기본                                    | 우리 책임                            |
| 탈출구                                   | MPL-2.0 자체 호스팅                                                                     | 비밀번호 해시 내보내기는 지원 요청·서명 필요                                              | AGPL-3.0 자체 호스팅                              | —                                    |

주요 출처: 각 제공자의 공개 discovery 문서(`auth.logto.io`, `samples.auth0.com`, `zitadel.cloud`), docs.logto.io(MFA·tenant-settings·Kakao·Naver·Management API), logto-io/logto 소스(`authentication-context-claims.ts`, `grants/refresh-token.ts`), auth0.com/docs(access-token-profiles, create-custom-claims, max-age-reauthentication, create-tenants), zitadel.com/docs(apis/openidoauth/claims), keycloak.org 26.x 업그레이드 노트, 각 가격 페이지, developers.kakao.com·developers.naver.com 프로필 API.

**알게 된 것 두 가지가 결정 1을 바꾼다.**

1. 관리형 후보 셋 모두 access token에 `auth_time`이 없다. 지금 구현(결정 1 §4, API가 access token의 `auth_time`을 확인)을 그대로 두면 관리형 제공자에서는 민감 작업마다 재로그인 요청이 끝없이 반복된다(2026-10-14 수정 이후 `auth_time`이 없으면 오래된 로그인으로 본다). → **결정 3**
2. 카카오 ID token에는 `email_verified`가 없고(이메일 검증 여부는 REST 사용자 정보의 `is_email_verified`), 네이버는 검증 플래그 자체가 없으며, Logto는 이메일이 있으면 `email_verified`를 참으로 준다. "`email_verified=false`면 로그인 거부"(결정 1 §7)는 제공자에 따라 소셜 로그인을 다 막거나 아무것도 막지 못한다. → **결정 4**

### 추천 (개정)

**Logto Cloud(JP East 리전) + 결정 3·4.** 결정 3으로 재인증이 제공자와 무관해지면 남는 차이는 한국 소셜 로그인·비용·탈출구인데, 카카오·네이버를 모두 내장하고, MAU와 무관한 고정 비용(약 $72/월)이며, MPL 자체 호스팅으로 옮길 수 있는 후보는 Logto뿐이다. Auth0는 1만 MAU에서 약 10배 비싸고 네이버가 없으며, Zitadel은 아시아 리전과 카카오·네이버 템플릿이 없다.

**대안: Keycloak 자체 호스팅.** access token에 `auth_time`이 있고 국내에 둘 수 있는 유일한 후보다. 클라우드가 정해지고 개인정보 보호법상 국외 이전을 피해야 한다고 판단되면 이쪽으로 간다(운영 부담을 감수).

리전: 어느 관리형 후보도 한국 리전이 없다. Logto JP East를 쓰면 회원 식별 정보가 일본에 저장되므로 privacy-requirements의 국외 이전 고지(이전 국가·항목·업체)에 넣어야 하고, 법률 검토에서 동의 방식을 정한다.

### 결정을 위한 확인 (Logto 테스트 테넌트, 1일)

문서에 없거나 코드로만 확인한 것들이다. 하나라도 어긋나면 Keycloak 대안으로 간다.

- [ ] `max_age=0`으로 인가하면 실제로 다시 로그인하고, 새 ID token의 `auth_time`이 그 시각인가 (결정 3의 전제)
- [ ] 카카오·네이버로 가입한 사용자의 ID token에 `email`이 오는가, 카카오 이메일 동의를 "필수"로 둘 수 있는가 (비즈 앱 전환 필요 여부)
- [ ] Management API의 사용자 삭제가 소셜 연결·MFA 등록까지 지우는가 (`account.purge`에서 호출)
- [ ] 우리 BFF의 토큰 갱신 주기(access token ≤ 15분)로 1만 MAU일 때 월 액세스 토큰 발급 수와 초과 비용
- [ ] DPA·SOC 2 보고서 확인, 하위 처리자(Azure·Cloudflare)를 고지 문구에 반영

## 결정 3 (제안): 재인증 시각은 API가 ID token으로 직접 확인해 보관한다

결정 1 §4를 대체한다. 제공자가 access token에 `auth_time`을 넣는지에 기대지 않는다.

1. 민감 엔드포인트가 `REAUTHENTICATION_REQUIRED`를 돌려주면 웹은 지금처럼 `max_age=0`(+ `prompt=login`)으로 다시 인가한다.
2. 콜백에서 BFF는 받은 **ID token을 그대로** API의 `POST /me/reauthentications`에 보낸다(본문, access token으로 인증).
3. API가 ID token을 검증한다: 같은 `OIDC_ISSUER`의 JWKS 서명, `aud` = 웹 클라이언트 ID, `sub`가 호출자와 같음, `auth_time`이 N분(초기 10분) 이내, 만료 전. 통과하면 사용자의 `reauthenticated_at`을 기록하고 감사 로그를 남긴다(토큰은 저장하지 않음).
4. `requireRecentAuthentication`은 `reauthenticated_at`(그리고 있다면 access token의 `auth_time` 중 더 최근 것)이 N분 이내인지 본다.

BFF는 아무것도 보증하지 않는다 — API가 IdP 서명을 직접 검증하므로 "API는 JWT만 믿는다"는 원칙이 유지된다. 새 테이블 대신 `users.reauthenticated_at` 열 하나면 되며(마이그레이션 추가), 로컬 mock IdP·Keycloak·Logto 모두 같은 경로를 탄다.

## 결정 4 (제안): 신원은 `(iss, sub)`만, 이메일은 로그인 조건이 아니다

결정 1 §7의 "`email_verified=false`면 로그인 거부"를 대체한다.

- 사용자 식별·첫 로그인 프로비저닝은 지금처럼 `(iss, sub)`로만 한다. **이메일로 계정을 찾거나 합치지 않는다**(이메일 기반 계정 연결은 탈취 경로).
- IdP가 준 이메일은 연락처 기본값일 뿐이다. 내보내기 머리글에 쓰는 이메일은 이미 사용자가 프로필(`user_profiles`)에 직접 넣는다.
- `email_verified`는 거부 조건으로 쓰지 않는다. 대신 IdP 쪽에서 이메일·비밀번호 가입을 끄거나(소셜·패스키만) 이메일 인증을 필수로 둔다 — 이것은 제공자 설정으로 강제하고 확인 항목에 넣는다.

## 결과

- 결정 1은 지금 착수 가능: `apps/api`에 resource server + `OidcWorkspaceResolver`, `apps/web`에 로그인 라우트·세션·헤더 주입, `infra/docker-compose.yml`에 로컬 Keycloak(realm import), `HeaderWorkspaceResolver`는 `local`·`test`에서만 유지.
- 결정 2가 나면 바뀌는 것은 env(`OIDC_ISSUER`, `OIDC_CLIENT_ID`, client secret)와 소셜 커넥션 설정뿐이다.
- 결정 3을 승인하면: `POST /me/reauthentications`(계약·구현), `users.reauthenticated_at` 마이그레이션, `requireRecentAuthentication` 변경, 웹 콜백에서 재인증 흐름일 때 ID token 전달. 승인 전까지 관리형 제공자로는 production을 띄울 수 없다(재인증 반복).
- 결정 4를 승인하면: `OidcWorkspaceResolver`의 `email_verified` 거부 제거, threat-model·privacy-requirements의 해당 문구 갱신.
- `docs/security/privacy-requirements.md`의 고지 문구에 제공자·리전을 채운다.

## 재검토 조건

팀·조직(멀티 workspace) 도입, 클라우드 확정, 제공자 가격·리전 정책 변경, 이상 탐지 요구가 제공자 기능을 넘어설 때. Logto가 access token의 `auth_time`·`acr`(현재 개발 플래그 뒤)을 정식 출시하면 결정 3을 단순화할 수 있다.
