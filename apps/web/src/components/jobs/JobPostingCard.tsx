import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { Icon } from "@/components/ui/Icon";
import { formatDate } from "@/lib/format";

type Posting = Schema<"JobPosting">;

/** J01 card: company, role, capture date and snapshot count (docs/design/screens.md). */
export function JobPostingCard({ posting }: { posting: Posting }) {
  const latest = posting.latestSnapshot;
  return (
    <li>
      <Link
        href={`/jobs/${posting.id}`}
        className="flex h-full flex-col gap-2 rounded-md border border-border-300 bg-surface-000 p-4 hover:bg-surface-050"
      >
        <span className="flex items-start justify-between gap-2">
          <span className="min-w-0">
            <span className="block truncate text-card-title">{posting.roleTitle}</span>
            <span className="block truncate text-body text-text-600">{posting.company}</span>
          </span>
          <Icon name="chevron-right" className="mt-1 shrink-0 text-text-600" />
        </span>
        {latest ? (
          <span className="line-clamp-3 text-caption text-text-600">{latest.textPreview}</span>
        ) : (
          <span className="text-caption text-text-600">저장된 본문이 없습니다.</span>
        )}
        <span className="mt-auto flex flex-wrap items-center gap-2 pt-1 text-caption text-text-600 tabular-nums">
          <Chip tone="snapshot">스냅샷 {posting.snapshotCount}</Chip>
          {latest ? <span>최근 수집 {formatDate(latest.capturedAt)}</span> : null}
        </span>
      </Link>
    </li>
  );
}
