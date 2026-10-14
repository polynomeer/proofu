import { NextResponse, type NextRequest } from "next/server";

import { API_ORIGIN, APP_ORIGIN, AUTH_MODE } from "@/lib/auth/config";
import { newSessionExpiry, readPkce, writeSession } from "@/lib/auth/session";
import { decodeIdToken, exchangeCode } from "@/lib/auth/tokens";

/**
 * Finishes the code flow: state check, code exchange, ID token checks, session cookies. Every
 * login also hands the ID token to the API so it can record the fresh login itself (ADR-0010
 * 결정 3) — hosted IdPs put no `auth_time` in access tokens. Identity is `(iss, sub)` only, so
 * `email_verified` does not gate the login (결정 4).
 */
export async function GET(request: NextRequest) {
  if (AUTH_MODE !== "oidc") return NextResponse.redirect(new URL("/", APP_ORIGIN));
  const params = request.nextUrl.searchParams;
  const pkce = await readPkce();
  const failed = (reason: string) =>
    NextResponse.redirect(new URL(`/auth/failed?reason=${reason}`, APP_ORIGIN));
  if (params.get("error")) return failed("provider");
  const code = params.get("code");
  if (!pkce || !code || params.get("state") !== pkce.state) return failed("state");

  try {
    const tokens = await exchangeCode(code, pkce.verifier, `${APP_ORIGIN}/auth/callback`);
    if (!tokens.id_token || !tokens.refresh_token) return failed("tokens");
    const claims = decodeIdToken(tokens.id_token, { nonce: pkce.nonce });
    const now = Math.floor(Date.now() / 1000);
    await writeSession(
      {
        sub: claims.sub,
        email: claims.email ?? null,
        name: claims.name ?? claims.preferred_username ?? null,
        refreshToken: tokens.refresh_token,
        authTime: claims.auth_time ?? now,
        expiresAt: newSessionExpiry(),
      },
      { accessToken: tokens.access_token, expiresAt: now + tokens.expires_in },
    );
    await recordLogin(tokens.access_token, tokens.id_token);
    return NextResponse.redirect(new URL(pkce.returnTo, APP_ORIGIN));
  } catch {
    return failed("exchange");
  }
}

/**
 * Best effort: a refusal (say, a silent SSO login with an old `auth_time`) only means the next
 * sensitive action asks for a fresh login, so it never fails the login itself.
 */
async function recordLogin(accessToken: string, idToken: string) {
  try {
    await fetch(`${API_ORIGIN}/api/v1/me/reauthentications`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
      cache: "no-store",
    });
  } catch {
    // The API being down is reported by the next request that needs it.
  }
}
