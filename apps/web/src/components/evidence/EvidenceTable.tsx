import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { SensitivityChip, VerificationChip } from "@/components/evidence/chips";
import { Chip } from "@/components/ui/Chip";
import { formatDate } from "@/lib/format";
import { evidenceTypeLabel } from "@/lib/labels";

type Evidence = Schema<"Evidence">;

/** E01: comparable rows use a table on desktop and cards below 768px (docs/design/components.md §카드와 표). */
export function EvidenceTable({ items }: { items: Evidence[] }) {
  return (
    <>
      <table className="hidden w-full border-separate border-spacing-0 rounded-md border border-border-300 bg-surface-000 text-body md:table">
        <thead className="text-left text-caption text-text-600">
          <tr>
            {["제목", "유형", "검증 상태", "민감도", "연결된 주장", "수집일"].map((h) => (
              <th key={h} scope="col" className="border-b border-border-300 px-4 py-3 font-medium">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {items.map((e) => (
            <tr key={e.id} className="hover:bg-surface-050">
              <td className="border-b border-border-300 px-4 py-3">
                <Link
                  href={`/evidence/${e.id}`}
                  className="font-semibold text-text-900 hover:underline"
                >
                  {e.title}
                </Link>
              </td>
              <td className="border-b border-border-300 px-4 py-3">
                <Chip>{evidenceTypeLabel(e.type)}</Chip>
              </td>
              <td className="border-b border-border-300 px-4 py-3">
                <VerificationChip value={e.verification} />
              </td>
              <td className="border-b border-border-300 px-4 py-3">
                <SensitivityChip value={e.sensitivity} />
              </td>
              <td className="border-b border-border-300 px-4 py-3 tabular-nums">
                {e.linkedClaimCount}개
              </td>
              <td className="border-b border-border-300 px-4 py-3 text-text-600 tabular-nums">
                {formatDate(e.capturedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <ul className="flex flex-col gap-3 md:hidden">
        {items.map((e) => (
          <li key={e.id} className="rounded-md border border-border-300 bg-surface-000 p-4">
            <Link href={`/evidence/${e.id}`} className="text-card-title hover:underline">
              {e.title}
            </Link>
            <div className="mt-2 flex flex-wrap gap-2">
              <Chip>{evidenceTypeLabel(e.type)}</Chip>
              <VerificationChip value={e.verification} />
              <SensitivityChip value={e.sensitivity} />
            </div>
            <p className="mt-2 text-caption text-text-600 tabular-nums">
              주장 {e.linkedClaimCount}개 · {formatDate(e.capturedAt)}
            </p>
          </li>
        ))}
      </ul>
    </>
  );
}
