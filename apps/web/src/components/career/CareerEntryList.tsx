import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { formatPeriod } from "@/lib/format";
import { careerEntryTypeLabel, visibilityLabel } from "@/lib/labels";

type Entry = Schema<"CareerEntry">;

/**
 * Timeline list (C01 default view), drawn like the dashboard timeline: period, a rail, then
 * the entry. Employment is a filled dot, everything else an outlined one; the type chip says
 * the same in words.
 */
export function CareerEntryList({ items }: { items: Entry[] }) {
  return (
    <ol className="flex flex-col rounded-md border border-border-300 bg-surface-000 px-4 py-5 md:px-6">
      {items.map((entry, i) => {
        const last = i === items.length - 1;
        return (
          <li
            key={entry.id}
            className="grid grid-cols-[16px_minmax(0,1fr)] gap-x-3 md:grid-cols-[144px_16px_minmax(0,1fr)]"
          >
            <span className="hidden pt-0.5 text-caption text-text-600 tabular-nums md:block">
              {formatPeriod(entry.startDate, entry.endDate)}
            </span>
            <span className="flex flex-col items-center" aria-hidden>
              <span
                className={[
                  "mt-1 h-3 w-3 shrink-0 rounded-full",
                  entry.type === "EMPLOYMENT"
                    ? "bg-primary-600"
                    : "border-2 border-primary-600 bg-surface-000",
                ].join(" ")}
              />
              {last ? null : <span className="w-0.5 flex-1 bg-border-300" />}
            </span>
            <div className={`flex min-w-0 flex-col gap-1.5 ${last ? "" : "pb-6"}`}>
              <Link
                href={`/career/${entry.id}`}
                className="self-start text-card-title hover:text-primary-600 hover:underline"
              >
                {entry.title}
              </Link>
              <span className="text-caption text-text-600 tabular-nums">
                <span className="md:hidden">{formatPeriod(entry.startDate, entry.endDate)}</span>
                {[entry.organization, entry.location].filter(Boolean).length > 0 ? (
                  <>
                    <span className="md:hidden"> · </span>
                    {[entry.organization, entry.location].filter(Boolean).join(" · ")}
                  </>
                ) : null}
              </span>
              {entry.description ? (
                <p className="line-clamp-2 text-body text-text-600">{entry.description}</p>
              ) : null}
              <span className="flex flex-wrap gap-1.5">
                <Chip>{careerEntryTypeLabel(entry.type)}</Chip>
                <Chip tone={entry.visibility === "PRIVATE" ? "private" : "neutral"}>
                  {visibilityLabel(entry.visibility)}
                </Chip>
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
