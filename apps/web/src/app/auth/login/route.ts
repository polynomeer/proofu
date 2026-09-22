import { NextResponse, type NextRequest } from "next/server";

import { APP_ORIGIN, AUTH_MODE, discover, oidcConfig } from "@/lib/auth/config";
import { writePkce } from "@/lib/auth/session";
import { pkcePair, randomToken } from "@/lib/auth/tokens";

/** Starts Authorization Code + PKCE. `?prompt=login` forces re-authentication (step-up); `?return=` is where to land. */
export async function GET(request: NextRequest) {
  if (AUTH_MODE !== "oidc") return NextResponse.redirect(new URL("/", APP_ORIGIN));
  const { clientId, scopes } = oidcConfig();
  const { authorization_endpoint } = await discover();
  const { verifier, challenge } = pkcePair();
  const state = randomToken();
  const nonce = randomToken();
  const returnTo = safeReturn(request.nextUrl.searchParams.get("return"));
  await writePkce({ state, nonce, verifier, returnTo });

  const url = new URL(authorization_endpoint);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", `${APP_ORIGIN}/auth/callback`);
  url.searchParams.set("scope", scopes);
  url.searchParams.set("state", state);
  url.searchParams.set("nonce", nonce);
  url.searchParams.set("code_challenge", challenge);
  url.searchParams.set("code_challenge_method", "S256");
  if (request.nextUrl.searchParams.get("prompt") === "login") {
    url.searchParams.set("prompt", "login");
    url.searchParams.set("max_age", "0");
  }
  return NextResponse.redirect(url);
}

/** Only same-origin paths; anything else goes home (open-redirect guard). */
function safeReturn(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/";
}
