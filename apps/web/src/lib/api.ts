import createClient, { type Middleware } from "openapi-fetch";

import { API_BASE_PATH, type paths } from "@proofu/contracts";

/**
 * Interim identity until OIDC lands (docs/api/conventions.md §인증): every call names the
 * workspace. The default is the id seeded by the API's local profile.
 */
const WORKSPACE_ID = process.env.NEXT_PUBLIC_WORKSPACE_ID ?? "00000000-0000-7000-8000-000000000002";

const workspaceHeader: Middleware = {
  onRequest({ request }) {
    request.headers.set("X-Workspace-Id", WORKSPACE_ID);
    return request;
  },
};

/** In the browser the dev server rewrites /api/*; on the server we call the API origin directly. */
function baseUrl(): string {
  if (typeof window !== "undefined") return API_BASE_PATH;
  return `${process.env.API_ORIGIN ?? "http://localhost:8080"}${API_BASE_PATH}`;
}

/** Typed client derived from packages/contracts/openapi.yaml. */
export const api = createClient<paths>({ baseUrl: baseUrl(), cache: "no-store" });
api.use(workspaceHeader);
