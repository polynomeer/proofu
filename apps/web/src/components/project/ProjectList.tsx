import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { Icon } from "@/components/ui/Icon";
import { formatMonth } from "@/lib/format";
import { visibilityLabel } from "@/lib/labels";

type Project = Schema<"Project">;

function period(p: Project): string {
  if (!p.startDate && !p.endDate) return "기간 미정";
  return `${p.startDate ? formatMonth(p.startDate) : "?"} – ${p.endDate ? formatMonth(p.endDate) : "현재"}`;
}

export function ProjectList({ items }: { items: Project[] }) {
  return (
    <ol className="divide-y divide-border-300 rounded-md border border-border-300 bg-surface-000">
      {items.map((p) => (
        <li key={p.id}>
          <Link
            href={`/projects/${p.id}`}
            className="grid grid-cols-[1fr_auto] items-start gap-4 px-4 py-4 hover:bg-surface-050 md:grid-cols-[140px_1fr_auto_auto]"
          >
            <span className="hidden text-caption text-text-600 tabular-nums md:block">
              {period(p)}
            </span>
            <span className="min-w-0">
              <span className="block truncate text-card-title">{p.name}</span>
              <span className="mt-0.5 block truncate text-caption text-text-600">
                {p.role}
                {p.teamSize ? ` · ${p.teamSize}명` : ""}
              </span>
              <span className="mt-1 line-clamp-2 block text-body text-text-600">{p.summary}</span>
            </span>
            <span className="hidden items-center md:flex">
              <Chip tone={p.visibility === "PRIVATE" ? "private" : "neutral"}>
                {visibilityLabel(p.visibility)}
              </Chip>
            </span>
            <Icon name="chevron-right" className="mt-1 text-text-600" />
          </Link>
        </li>
      ))}
    </ol>
  );
}
