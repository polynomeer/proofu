import path from "node:path";

import type { NextConfig } from "next";

import { STATIC_SECURITY_HEADERS } from "./src/lib/security-headers";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // The container image runs `node server.js` with only the traced dependencies
  // (infra/docker/web.Dockerfile, ADR-0012). The tracing root is the repository, not this
  // package: the workspace dependencies live in the root node_modules store.
  output: "standalone",
  outputFileTracingRoot: path.join(import.meta.dirname, "../.."),
  // Type-only import of the contract package; nothing to transpile at runtime.
  transpilePackages: ["@proofu/contracts"],
  // /api/* is served by app/api/[...path]/route.ts (the BFF proxy), not a rewrite: it must
  // attach credentials server-side.
  // The CSP is per-request (nonce) and set in proxy.ts; these do not vary.
  async headers() {
    return [{ source: "/:path*", headers: STATIC_SECURITY_HEADERS }];
  },
};

export default nextConfig;
