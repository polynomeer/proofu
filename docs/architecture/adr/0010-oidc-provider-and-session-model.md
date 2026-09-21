# 0010 인증: OIDC 연동 형태와 제공자

- 상태: **제안** (제공자 선택은 결정 대기, 연동 형태는 결정 제안)
- 일자: 2026-09-21

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

### 추천

**B(관리형 오픈소스 IdP, 1순위 Logto Cloud)를 검증하고, 검증 실패 시 A.** 근거: 카카오·네이버가 내장돼 한국 중심 요구를 바로 충족하고, 클라우드 중립이며, 자체 호스팅 탈출구가 있다. 검증 항목은 아래 "결정을 위한 확인"이며, 하나라도 미달이면 A로 간다. D는 클라우드 결정 이후에만 재검토한다. E는 채택하지 않는다.

### 결정을 위한 확인 (제공자 후보마다, 무료 티어로 1일)

- [ ] Discovery 문서에 PKCE(S256), `end_session_endpoint`, `auth_time` 클레임이 있는가
- [ ] `max_age=0`/`prompt=login`으로 재인증을 강제하고 `auth_time`이 갱신되는가
- [ ] 관리 API로 사용자를 삭제할 수 있는가(계정 삭제 잡에서 호출)
- [ ] 카카오·네이버 로그인 후 `email`·`email_verified`가 오는가 (카카오는 이메일 동의 항목 설정 필요)
- [ ] MFA(TOTP 또는 패스키)를 사용자가 스스로 켤 수 있는가
- [ ] 데이터 저장 리전을 명시하고 DPA를 제공하는가
- [ ] MAU 1천/1만에서의 월 비용

## 결과

- 결정 1은 지금 착수 가능: `apps/api`에 resource server + `OidcWorkspaceResolver`, `apps/web`에 로그인 라우트·세션·헤더 주입, `infra/docker-compose.yml`에 로컬 Keycloak(realm import), `HeaderWorkspaceResolver`는 `local`·`test`에서만 유지.
- 결정 2가 나면 바뀌는 것은 env(`OIDC_ISSUER`, `OIDC_CLIENT_ID`, client secret)와 소셜 커넥션 설정뿐이다.
- `docs/security/privacy-requirements.md`의 고지 문구에 제공자·리전을 채운다.

## 재검토 조건

팀·조직(멀티 workspace) 도입, 클라우드 확정, 제공자 가격·리전 정책 변경, 이상 탐지 요구가 제공자 기능을 넘어설 때.
