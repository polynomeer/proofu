import createClient, { type Middleware } from "openapi-fetch";

import { API_BASE_PATH, type paths } from "@proofu/contracts";

/**
 * Credentials never live in the browser (ADR-0010). In the browser every call goes to the
 * same-origin BFF proxy (app/api/[...path]) which attaches them; on the server
 * `@/lib/auth/server-api` (imported by the root layout) attaches them from the session cookie.
 */
const expiredSession: Middleware = {
  onResponse({ response }) {
    // A session that expired mid-use: send the browser back through login and return here.
    if (
      typeof window !== "undefined" &&
      response.status === 401 &&
      !window.location.pathname.startsWith("/auth/")
    ) {
      const back = encodeURIComponent(window.location.pathname + window.location.search);
      // A full navigation on purpose: the login route is a redirect to the IdP, not a Next page.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`/auth/login?return=${back}`);
    }
    return response;
  },
};

function baseUrl(): string {
  if (typeof window !== "undefined") return API_BASE_PATH;
  return `${process.env.API_ORIGIN ?? "http://localhost:8080"}${API_BASE_PATH}`;
}

/** Typed client derived from packages/contracts/openapi.yaml. */
export const api = createClient<paths>({ baseUrl: baseUrl(), cache: "no-store" });
api.use(expiredSession);
