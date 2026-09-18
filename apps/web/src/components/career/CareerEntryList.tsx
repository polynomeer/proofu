import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { Icon } from "@/components/ui/Icon";
import { formatPeriod } from "@/lib/format";
import { careerEntryTypeLabel, visibilityLabel } from "@/lib/labels";

type Entry = Schema<"CareerEntry">;

/** Timeline list (C01 default view). Whole row is the link; keyboard focus stays visible. */
export function CareerEntryList({ items }: { items: Entry[] }) {
  return (
    <ol className="divide-y divide-border-300 rounded-md border border-border-300 bg-surface-000">
      {items.map((entry) => (
        <li key={entry.id}>
          <Link
            href={`/career/${entry.id}`}
            className="grid grid-cols-[auto_1fr_auto] items-start gap-4 px-4 py-4 hover:bg-surface-050 md:grid-cols-[160px_1fr_auto_auto]"
          >
            <span className="flex items-center gap-3 text-caption text-text-600 tabular-nums">
              <span
                className="mt-px h-2.5 w-2.5 shrink-0 rounded-full bg-primary-600"
                aria-hidden
              />
              {formatPeriod(entry.startDate, entry.endDate)}
            </span>
            <span className="min-w-0">
              <span className="block truncate text-card-title">{entry.title}</span>
              <span className="mt-0.5 block truncate text-caption text-text-600">
                {[entry.organization, entry.location].filter(Boolean).join(" · ") || " "}
              </span>
              {entry.description ? (
                <span className="mt-1 line-clamp-2 block text-body text-text-600">
                  {entry.description}
                </span>
              ) : null}
            </span>
            <span className="hidden items-center gap-2 md:flex">
              <Chip>{careerEntryTypeLabel(entry.type)}</Chip>
              {entry.visibility === "PRIVATE" ? (
                <Chip tone="private">{visibilityLabel(entry.visibility)}</Chip>
              ) : (
                <Chip>{visibilityLabel(entry.visibility)}</Chip>
              )}
            </span>
            <Icon name="chevron-right" className="mt-1 text-text-600" />
          </Link>
        </li>
      ))}
    </ol>
  );
}
