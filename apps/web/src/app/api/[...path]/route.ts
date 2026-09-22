import { NextResponse, type NextRequest } from "next/server";

import { API_ORIGIN, AUTH_MODE, WORKSPACE_ID } from "@/lib/auth/config";
import { currentAccessToken } from "@/lib/auth/current";

/**
 * BFF proxy (ADR-0010 결정 1 §1): every browser call to /api/* passes through here so the
 * credential — bearer token in oidc mode, X-Workspace-Id in header mode — is attached
 * server-side. Bodies and headers stream through unchanged, including file downloads.
 */
async function proxy(request: NextRequest) {
  const headers = new Headers();
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const accept = request.headers.get("accept");
  if (accept) headers.set("accept", accept);

  if (AUTH_MODE === "oidc") {
    const token = await currentAccessToken(true);
    if (!token) {
      return NextResponse.json(
        {
          type: "https://proofu.dev/problems/unauthenticated",
          title: "Unauthorized",
          status: 401,
          detail: "로그인이 필요합니다.",
          code: "UNAUTHENTICATED",
        },
        { status: 401 },
      );
    }
    headers.set("authorization", `Bearer ${token}`);
  } else {
    headers.set("x-workspace-id", WORKSPACE_ID);
  }

  const target = new URL(request.nextUrl.pathname + request.nextUrl.search, API_ORIGIN);
  const upstream = await fetch(target, {
    method: request.method,
    headers,
    body:
      request.method === "GET" || request.method === "HEAD"
        ? undefined
        : await request.arrayBuffer(),
    redirect: "manual",
    cache: "no-store",
  });
  const out = new Headers();
  for (const name of [
    "content-type",
    "content-disposition",
    "cache-control",
    "location",
    "www-authenticate",
  ]) {
    const v = upstream.headers.get(name);
    if (v) out.set(name, v);
  }
  return new NextResponse(upstream.body, { status: upstream.status, headers: out });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
