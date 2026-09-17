import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Type-only import of the contract package; nothing to transpile at runtime.
  transpilePackages: ["@proofu/contracts"],
  async rewrites() {
    // Local development proxies API calls to the Spring Boot service to avoid CORS.
    const api = process.env.API_ORIGIN ?? "http://localhost:8080";
    return [{ source: "/api/:path*", destination: `${api}/api/:path*` }];
  },
};

export default nextConfig;
