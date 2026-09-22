import { NextResponse, type NextRequest } from "next/server";

import { APP_ORIGIN, AUTH_MODE } from "@/lib/auth/config";
import { newSessionExpiry, readPkce, writeSession } from "@/lib/auth/session";
import { decodeIdToken, exchangeCode } from "@/lib/auth/tokens";

/** Finishes the code flow: state check, code exchange, ID token checks, session cookies. */
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
    if (claims.email_verified === false) return failed("email");
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
    return NextResponse.redirect(new URL(pkce.returnTo, APP_ORIGIN));
  } catch {
    return failed("exchange");
  }
}
