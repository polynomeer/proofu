import { NextResponse, type NextRequest } from "next/server";

import { contentSecurityPolicy } from "@/lib/security-headers";

/**
 * Two jobs on every request: a per-request CSP nonce (docs/security/threat-model.md) and, in
 * oidc mode, the session gate — no session cookie → login, returning here afterwards. The
 * cookie's contents are verified by the routes that use it; this only checks presence.
 */
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const csp = contentSecurityPolicy(
    nonce,
    process.env.NODE_ENV !== "production",
    originOf(process.env.OIDC_ISSUER),
  );
  // Next reads the nonce back out of this request header to stamp its own script tags.
  const headers = new Headers(request.headers);
  headers.set("x-nonce", nonce);
  headers.set("content-security-policy", csp);

  const withPolicy = (response: NextResponse) => {
    response.headers.set("content-security-policy", csp);
    return response;
  };
  const proceed = () => withPolicy(NextResponse.next({ request: { headers } }));

  if (process.env.AUTH_MODE !== "oidc") return proceed();
  const { pathname, search } = request.nextUrl;
  if (
    pathname.startsWith("/auth/") ||
    pathname.startsWith("/api/") ||
    pathname.startsWith("/_next/")
  ) {
    return proceed();
  }
  if (request.cookies.has("proofu_session")) return proceed();
  const login = new URL("/auth/login", request.url);
  login.searchParams.set("return", pathname + search);
  return withPolicy(NextResponse.redirect(login));
}

/** The issuer's origin; an unset or malformed issuer simply adds nothing to the policy. */
function originOf(issuer: string | undefined): string | undefined {
  if (!issuer) return undefined;
  try {
    return new URL(issuer).origin;
  } catch {
    return undefined;
  }
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
