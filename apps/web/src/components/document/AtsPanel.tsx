import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { atsSeverityLabel } from "@/lib/labels";

type Report = Schema<"AtsReport">;
type Severity = Schema<"AtsSeverity">;

export function atsTone(s: Severity) {
  return s === "PASS" ? "verified" : s === "WARN" ? "review" : "neutral";
}

/** R01 "ATS 검사": one row per check, facts only — never a pass/fail probability. */
export function AtsPanel({ report }: { report: Report | null }) {
  return (
    <div className="rounded-md border border-border-300 bg-surface-000 p-4">
      <h2 className="text-card-title">ATS 검사</h2>
      <p className="mt-1 text-caption text-text-600">
        최신 버전을 자동 채용 시스템이 읽을 수 있는지 항목별로 확인합니다. 합격 가능성이 아닙니다.
      </p>
      {report === null ? (
        <p className="mt-3 text-caption text-text-600">저장된 버전이 없습니다.</p>
      ) : (
        <>
          <p className="mt-2 text-caption">
            {report.warnings > 0 ? (
              <span className="text-warning-700">주의 {report.warnings}건</span>
            ) : (
              <span className="text-success-700">주의 사항 없음</span>
            )}
          </p>
          <ul className="mt-2 flex flex-col divide-y divide-border-300">
            {report.findings.map((f) => (
              <li key={f.code} className="flex flex-col gap-1 py-2 text-caption">
                <span className="flex items-center gap-2">
                  <Chip tone={atsTone(f.severity)}>{atsSeverityLabel(f.severity)}</Chip>
                  <span className="text-body">{f.message}</span>
                </span>
                {f.details.length > 0 ? (
                  <span className="pl-1 text-text-600">{f.details.join(" · ")}</span>
                ) : null}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
