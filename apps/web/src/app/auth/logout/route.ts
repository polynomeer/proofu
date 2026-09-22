import { NextResponse, type NextRequest } from "next/server";

import { APP_ORIGIN, AUTH_MODE, discover, oidcConfig } from "@/lib/auth/config";
import { clearSession } from "@/lib/auth/session";

/** Drops the local session, then ends the IdP session (RP-initiated logout) when it offers one. */
export async function POST(request: NextRequest) {
  await clearSession();
  if (AUTH_MODE !== "oidc") return NextResponse.redirect(new URL("/", APP_ORIGIN), 303);
  const { end_session_endpoint } = await discover();
  if (!end_session_endpoint)
    return NextResponse.redirect(new URL("/auth/signed-out", APP_ORIGIN), 303);
  const url = new URL(end_session_endpoint);
  url.searchParams.set("client_id", oidcConfig().clientId);
  url.searchParams.set("post_logout_redirect_uri", `${APP_ORIGIN}/auth/signed-out`);
  void request;
  return NextResponse.redirect(url, 303);
}
