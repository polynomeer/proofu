import "server-only";

import type { Middleware } from "openapi-fetch";

import { api } from "@/lib/api";
import { AUTH_MODE, WORKSPACE_ID } from "@/lib/auth/config";
import { currentAccessToken } from "@/lib/auth/current";

/**
 * Server-side credential middleware. Imported once by the root layout so it is registered in
 * the server process only; client bundles never see this module (server-only).
 */
const credentials: Middleware = {
  async onRequest({ request }) {
    if (AUTH_MODE === "oidc") {
      const token = await currentAccessToken(false);
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
    } else {
      request.headers.set("X-Workspace-Id", WORKSPACE_ID);
    }
    return request;
  },
};

declare global {
  var proofuServerApiRegistered: boolean | undefined;
}

if (!globalThis.proofuServerApiRegistered) {
  api.use(credentials);
  globalThis.proofuServerApiRegistered = true;
}
