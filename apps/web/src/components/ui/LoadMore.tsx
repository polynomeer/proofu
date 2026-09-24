"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";

/**
 * Pages an interactive list in place. A list that simply stops at its first page hides records
 * the user entered, so every capped list either shows this or says what it is showing.
 */
export function LoadMore<T>({
  cursor,
  fetchPage,
  onLoaded,
  label = "더 보기",
}: {
  cursor: string | null;
  fetchPage: (cursor: string) => Promise<{ items: T[]; nextCursor?: string | null }>;
  onLoaded: (items: T[], nextCursor: string | null) => void;
  label?: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!cursor) return null;

  return (
    <div className="flex items-center gap-3">
      <Button
        variant="secondary"
        loading={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            const page = await fetchPage(cursor);
            onLoaded(page.items, page.nextCursor ?? null);
          } catch {
            setError("더 불러오지 못했습니다. 잠시 후 다시 시도하세요.");
          } finally {
            setBusy(false);
          }
        }}
      >
        {label}
      </Button>
      {error ? (
        <span role="alert" className="text-caption text-warning-700">
          {error}
        </span>
      ) : null}
    </div>
  );
}
