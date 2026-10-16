import Link from "next/link";

import { filterChipClass } from "@/components/ui/filterChip";

/** Filter chips reflected in the URL so the view can be shared and restored. */
export function PromotedFilter({ current }: { current?: boolean }) {
  const options: { value?: boolean; label: string }[] = [
    { label: "전체" },
    { value: false, label: "승격 대기" },
    { value: true, label: "승격함" },
  ];
  return (
    <nav aria-label="승격 여부 필터" className="flex flex-wrap gap-2">
      {options.map(({ value, label }) => {
        const active = current === value;
        const href = value === undefined ? "/career/logs" : `/career/logs?promoted=${value}`;
        return (
          <Link
            key={label}
            href={href}
            aria-current={active ? "true" : undefined}
            className={filterChipClass(active)}
          >
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
