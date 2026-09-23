import type { NextConfig } from "next";

import { STATIC_SECURITY_HEADERS } from "./src/lib/security-headers";

const nextConfig: NextConfig = {
  reactStrictMode: true,
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
