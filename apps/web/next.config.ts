import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Type-only import of the contract package; nothing to transpile at runtime.
  transpilePackages: ["@proofu/contracts"],
  // /api/* is served by app/api/[...path]/route.ts (the BFF proxy), not a rewrite: it must
  // attach credentials server-side.
};

export default nextConfig;
