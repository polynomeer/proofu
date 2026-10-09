import Link from "next/link";

import { Icon, type IconName } from "@/components/ui/Icon";

/**
 * KPI card (docs/design/screens.md §D01). The whole card links to its list; change is
 * shown as text ("최근 30일 +3") rather than by colour alone. `progress` (0–100) draws a bar
 * under the number for ratio metrics; the number itself stays the value of record.
 */
export function KpiCard({
  icon,
  label,
  value,
  unit,
  delta,
  caption,
  href,
  progress,
}: {
  icon: IconName;
  label: string;
  value: number | string;
  unit?: string;
  delta?: string;
  caption: string;
  href: string;
  progress?: number;
}) {
  return (
    <Link
      href={href}
      className="flex flex-col gap-2 rounded-md border border-border-300 bg-surface-000 px-5 py-4 hover:bg-surface-050"
    >
      <span className="flex items-center gap-2 text-body text-text-600">
        <Icon name={icon} size={16} className="text-primary-600" />
        {label}
      </span>
      <span className="flex items-baseline gap-2">
        <span className="text-stat tabular-nums">
          {value}
          {unit ? <span className="text-section-title">{unit}</span> : null}
        </span>
        {delta ? <span className="text-caption text-success-700">▲ {delta}</span> : null}
      </span>
      {progress !== undefined ? (
        <span className="block h-1.5 overflow-hidden rounded-full bg-surface-100" aria-hidden>
          <span
            className="block h-full rounded-full bg-primary-600"
            style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
          />
        </span>
      ) : null}
      <span className="block text-caption text-text-600">{caption}</span>
    </Link>
  );
}
