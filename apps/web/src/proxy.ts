import { NextResponse, type NextRequest } from "next/server";

/**
 * Gate for app pages in oidc mode: no session cookie → login, returning here afterwards.
 * The cookie's contents are verified by the routes that use it; this only checks presence.
 */
export function proxy(request: NextRequest) {
  if (process.env.AUTH_MODE !== "oidc") return NextResponse.next();
  const { pathname, search } = request.nextUrl;
  if (
    pathname.startsWith("/auth/") ||
    pathname.startsWith("/api/") ||
    pathname.startsWith("/_next/")
  ) {
    return NextResponse.next();
  }
  if (request.cookies.has("proofu_session")) return NextResponse.next();
  const login = new URL("/auth/login", request.url);
  login.searchParams.set("return", pathname + search);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
