"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { inputClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { EXPORT_FORMATS, exportFormatLabel, exportStatusLabel } from "@/lib/labels";

type Export = Schema<"Export">;
type Format = Schema<"ExportFormat">;

/** Formats the renderer module does not produce yet; none today (ADR-0009). */
const UNAVAILABLE: readonly Format[] = [];

const EXPORT_ERRORS: Record<string, string> = {
  UNSUPPORTED_CLAIM_IN_EXPORT: "승인되지 않은 문장이 있어 내보내지 않았습니다.",
  RENDER_VALIDATION_FAILED: "렌더링 결과에서 문장을 다시 찾지 못해 파일을 만들지 않았습니다.",
  EXPORT_TOO_LARGE: "파일이 5MB를 넘어 저장하지 않았습니다.",
  RENDER_FAILED: "파일을 만들지 못했습니다. 다시 시도하세요.",
  FORMAT_NOT_IMPLEMENTED: "아직 지원하지 않는 형식입니다.",
};

function statusTone(s: Export["status"]) {
  return s === "READY"
    ? "verified"
    : s === "FAILED"
      ? "review"
      : s === "EXPIRED"
        ? "expired"
        : "neutral";
}

function formatBytes(n: number | undefined) {
  if (n == null) return "";
  return n >= 1024 * 1024
    ? `${(n / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1024))} KB`;
}

function DownloadButton({ item }: { item: Export }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** The API needs the workspace header, so the file is fetched through the client and saved from a blob. */
  async function download() {
    setBusy(true);
    setError(null);
    const { data, error: problem } = await api.GET("/exports/{id}/file", {
      params: { path: { id: item.id } },
      parseAs: "blob",
    });
    setBusy(false);
    if (!data) {
      setError(problem?.detail ?? "내려받지 못했습니다.");
      return;
    }
    const url = URL.createObjectURL(data);
    const a = document.createElement("a");
    a.href = url;
    a.download = item.fileName;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <span className="flex flex-col items-end">
      <Button
        variant="tertiary"
        className="h-7 px-2 text-caption"
        onClick={download}
        loading={busy}
      >
        내려받기
      </Button>
      {error ? (
        <span role="alert" className="text-caption text-warning-700">
          {error}
        </span>
      ) : null}
    </span>
  );
}

/** F07: request a rendering of the latest version and list what has been rendered so far. */
export function ExportPanel({
  versionId,
  pendingApproval,
  initialItems,
}: {
  versionId: string | null;
  pendingApproval: number;
  initialItems: Export[];
}) {
  const router = useRouter();
  const [format, setFormat] = useState<Format>("DOCX");
  const blocked =
    versionId === null
      ? "저장된 버전이 없습니다."
      : pendingApproval > 0
        ? `승인 필요 블록 ${pendingApproval}개를 먼저 승인하고 버전을 저장하세요.`
        : UNAVAILABLE.includes(format)
          ? "아직 지원하지 않는 형식입니다."
          : null;

  return (
    <div className="rounded-md border border-border-300 bg-surface-000 p-4">
      <h2 className="text-card-title">내보내기</h2>
      <p className="mt-1 text-caption text-text-600">
        최신 버전을 파일로 만듭니다. 근거 없음·추론 문장은 승인된 것만 들어갑니다.
      </p>
      <div className="mt-3 flex flex-wrap items-end gap-2">
        <select
          aria-label="내보내기 형식"
          className={`${inputClass} w-40`}
          value={format}
          onChange={(e) => setFormat(e.target.value as Format)}
        >
          {EXPORT_FORMATS.map((f) => (
            <option key={f} value={f}>
              {exportFormatLabel(f)}
              {UNAVAILABLE.includes(f) ? " (준비 중)" : ""}
            </option>
          ))}
        </select>
        <AsyncJobButton
          variant="secondary"
          label="파일 만들기"
          icon={<Icon name="document" size={16} />}
          runningText="렌더링하고 텍스트를 다시 추출해 검증하는 중…"
          disabled={blocked !== null}
          title={blocked ?? undefined}
          errors={EXPORT_ERRORS}
          summary={(r) => (r.reused ? "이미 만든 파일을 다시 씁니다." : "파일이 준비되었습니다.")}
          start={async () => {
            if (!versionId) return { error: "저장된 버전이 없습니다." };
            const { data, error } = await api.POST("/document-versions/{id}/exports", {
              params: { path: { id: versionId } },
              body: { format },
            });
            return { jobId: data?.jobId, error: error?.detail };
          }}
        />
      </div>
      {blocked && versionId !== null && pendingApproval > 0 ? (
        <p className="mt-2 text-caption text-warning-700">{blocked}</p>
      ) : null}
      {initialItems.length === 0 ? (
        <p className="mt-3 text-caption text-text-600">아직 만든 파일이 없습니다.</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-border-300">
          {initialItems.map((item) => (
            <li key={item.id} className="flex flex-wrap items-center gap-2 py-2 text-caption">
              <Chip tone={statusTone(item.status)}>{exportStatusLabel(item.status)}</Chip>
              <span className="min-w-0 flex-1">
                <span className="text-body">{exportFormatLabel(item.format)}</span>
                <span className="block text-text-600">
                  {formatDateTime(item.createdAt)}
                  {item.sizeBytes != null ? ` · ${formatBytes(item.sizeBytes)}` : ""}
                  {item.errorCode ? ` · ${EXPORT_ERRORS[item.errorCode] ?? item.errorCode}` : ""}
                </span>
              </span>
              {item.status === "READY" ? <DownloadButton item={item} /> : null}
            </li>
          ))}
        </ul>
      )}
      {initialItems.some(
        (i) => i.status === "REQUESTED" || i.status === "RENDERING" || i.status === "VALIDATING",
      ) ? (
        <Button
          variant="tertiary"
          className="mt-2 h-7 px-2 text-caption"
          onClick={() => router.refresh()}
        >
          상태 새로고침
        </Button>
      ) : null}
    </div>
  );
}
