import type { ReactNode } from "react";

import { BottomNav } from "@/components/app-shell/BottomNav";
import { Sidebar } from "@/components/app-shell/Sidebar";
import { TopBar } from "@/components/app-shell/TopBar";

/** Authenticated app shell: fixed sidebar, sticky top bar, content capped at 1440px. */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar userName="Dev User" />
        <main className="mx-auto w-full max-w-content flex-1 px-4 py-6 pb-20 md:px-6 md:pb-6">
          {children}
        </main>
      </div>
      <BottomNav />
    </div>
  );
}
