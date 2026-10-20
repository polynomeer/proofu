import Link from "next/link";

import { filterChipClass } from "@/components/ui/filterChip";

import { CONTACT_RELATIONS, contactRelationLabel } from "@/lib/labels";

/** Filter chips reflected in the URL so the view can be shared and restored. */
export function RelationFilter({ current }: { current?: string }) {
  const options: { value?: string; label: string }[] = [
    { label: "전체" },
    ...CONTACT_RELATIONS.map((r) => ({ value: r, label: contactRelationLabel(r) })),
  ];
  return (
    <nav aria-label="관계 필터" className="flex flex-wrap gap-2">
      {options.map(({ value, label }) => (
        <Link
          key={label}
          href={value ? `/career/contacts?relation=${value}` : "/career/contacts"}
          aria-current={(current ?? undefined) === value ? "true" : undefined}
          className={filterChipClass((current ?? undefined) === value)}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}
