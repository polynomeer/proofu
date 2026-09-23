import type { ReactNode } from "react";

import { BottomNav } from "@/components/app-shell/BottomNav";
import { Sidebar } from "@/components/app-shell/Sidebar";
import { TopBar } from "@/components/app-shell/TopBar";
import { currentCaller } from "@/lib/auth/current";

/**
 * Authenticated app shell: fixed sidebar, sticky top bar, content capped at 1440px. The skip
 * link is the first thing a keyboard reaches so the nav can be bypassed (WCAG 2.4.1).
 */
export default async function AppLayout({ children }: { children: ReactNode }) {
  const caller = await currentCaller();
  return (
    <div className="flex min-h-dvh">
      <a
        href="#main"
        className="sr-only rounded-md bg-surface-000 px-4 py-2 text-body font-semibold text-primary-600 focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50"
      >
        본문으로 건너뛰기
      </a>
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar userName={caller?.name ?? "로그인 필요"} canSignOut={caller?.mode === "oidc"} />
        <main
          id="main"
          tabIndex={-1}
          className="mx-auto w-full max-w-content flex-1 px-4 py-6 pb-20 md:px-6 md:pb-6"
        >
          {children}
        </main>
      </div>
      <BottomNav />
    </div>
  );
}
