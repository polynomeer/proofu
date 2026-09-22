import type { Metadata } from "next";
import type { ReactNode } from "react";

import "@/styles/tokens.css";
// Registers the server-side credential middleware for @/lib/api (ADR-0010).
import "@/lib/auth/server-api";

export const metadata: Metadata = {
  title: {
    default: "ProofU",
    template: "%s · ProofU",
  },
  description: "증거로 완성하는 커리어의 새로운 기준, ProofU",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css"
          crossOrigin="anonymous"
        />
      </head>
      <body className="min-h-dvh antialiased">{children}</body>
    </html>
  );
}
