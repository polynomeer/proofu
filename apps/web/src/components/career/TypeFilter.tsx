import Link from "next/link";

import { CAREER_ENTRY_TYPES, careerEntryTypeLabel } from "@/lib/labels";

/** Filter chips reflected in the URL so the view can be shared and restored. */
export function TypeFilter({ current, q }: { current?: string; q?: string }) {
  const options: { value?: string; label: string }[] = [
    { label: "전체" },
    ...CAREER_ENTRY_TYPES.map((t) => ({ value: t, label: careerEntryTypeLabel(t) })),
  ];
  return (
    <nav aria-label="유형 필터" className="flex flex-wrap gap-2">
      {options.map(({ value, label }) => {
        const active = (current ?? undefined) === value;
        const params = new URLSearchParams();
        if (value) params.set("type", value);
        if (q) params.set("q", q);
        const query = params.toString();
        return (
          <Link
            key={label}
            href={query ? `/career?${query}` : "/career"}
            aria-current={active ? "true" : undefined}
            className={[
              "inline-flex h-9 items-center rounded-md border px-3 text-body",
              active
                ? "border-primary-600 bg-primary-600 font-semibold text-white"
                : "border-border-300 bg-surface-000 text-text-900 hover:bg-surface-050",
            ].join(" ")}
          >
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
