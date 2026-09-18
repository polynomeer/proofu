import Link from "next/link";

import { Icon, type IconName } from "@/components/ui/Icon";

/**
 * KPI card (docs/design/screens.md §D01). The whole card links to its list; change is
 * shown as text ("최근 30일 +3") rather than by colour alone.
 */
export function KpiCard({
  icon,
  label,
  value,
  unit,
  delta,
  caption,
  href,
}: {
  icon: IconName;
  label: string;
  value: number | string;
  unit?: string;
  delta?: string;
  caption: string;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="flex gap-4 rounded-md border border-border-300 bg-surface-000 p-4 hover:bg-surface-050"
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary-050 text-primary-600">
        <Icon name={icon} />
      </span>
      <span className="min-w-0">
        <span className="block text-body font-semibold">{label}</span>
        <span className="mt-1 flex items-baseline gap-2">
          <span className="text-stat tabular-nums">
            {value}
            {unit ? <span className="text-section-title">{unit}</span> : null}
          </span>
          {delta ? <span className="text-caption text-success-700">{delta}</span> : null}
        </span>
        <span className="mt-1 block text-caption text-text-600">{caption}</span>
      </span>
    </Link>
  );
}
