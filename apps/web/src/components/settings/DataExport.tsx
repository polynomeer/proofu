"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { exportStatusLabel } from "@/lib/labels";

type AccountExport = Schema<"AccountExport">;

const ERRORS: Record<string, string> = {
  EXPORT_TOO_LARGE: "묶음이 200MB를 넘어 만들지 못했습니다.",
  EXPORT_NOT_FOUND: "내보내기 요청을 찾지 못했습니다.",
};

function statusTone(s: AccountExport["status"]) {
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

function isExpired(item: AccountExport, now = Date.now()) {
  return item.expiresAt != null && new Date(item.expiresAt).getTime() < now;
}

/** The archive holds everything the user has; the API insists on a recent login to hand it out. */
function DownloadButton({ item }: { item: AccountExport }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function download() {
    setBusy(true);
    setError(null);
    const {
      data,
      error: problem,
      response,
    } = await api.GET("/me/exports/{id}/file", {
      params: { path: { id: item.id } },
      parseAs: "blob",
    });
    setBusy(false);
    if (!data) {
      if (problem?.code === "REAUTHENTICATION_REQUIRED" || response.status === 401) {
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign("/auth/login?prompt=login&return=/settings");
        return;
      }
      setError(problem?.detail ?? "내려받지 못했습니다.");
      return;
    }
    const url = URL.createObjectURL(data);
    const a = document.createElement("a");
    a.href = url;
    a.download = `proofu-data-${item.createdAt.slice(0, 10)}.zip`;
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
        ZIP 내려받기
      </Button>
      {error ? (
        <span role="alert" className="text-caption text-warning-700">
          {error}
        </span>
      ) : null}
    </span>
  );
}

/**
 * S01 full data export (docs/data/retention.md): one job per request, the archive lives 7 days,
 * downloading it needs a recent login.
 */
export function DataExport({ initialItems }: { initialItems: AccountExport[] }) {
  const router = useRouter();
  const pending = initialItems.some((i) => i.status === "REQUESTED");

  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <h2 className="text-section-title">데이터 내보내기</h2>
      <p className="mt-1 mb-4 text-caption text-text-600">
        경력·Evidence·공고·지원·문서·제출 스냅샷·설정·감사 기록을 <code>data.json</code>으로, 만든
        문서 파일을 <code>exports/</code>로 묶은 ZIP을 만듭니다. 파일은 7일 뒤 만료되고,
        내려받으려면 최근 10분 안에 로그인한 상태여야 합니다.
      </p>
      <AsyncJobButton
        label="ZIP 만들기"
        icon={<Icon name="document" size={16} />}
        runningText="묶는 중…"
        errors={ERRORS}
        summary={(r) =>
          typeof r.sizeBytes === "number" && r.sizeBytes > 0
            ? `준비되었습니다 (${formatBytes(r.sizeBytes)}).`
            : "준비되었습니다."
        }
        start={async () => {
          const { data, error } = await api.POST("/me/exports", {});
          return { jobId: data?.jobId, error: error?.detail };
        }}
      />
      {initialItems.length === 0 ? (
        <p className="mt-3 text-caption text-text-600">아직 만든 묶음이 없습니다.</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-border-300">
          {initialItems.map((item) => {
            const status = item.status === "READY" && isExpired(item) ? "EXPIRED" : item.status;
            const tables = item.tables ? Object.values(item.tables).reduce((a, b) => a + b, 0) : 0;
            return (
              <li key={item.id} className="flex flex-wrap items-center gap-2 py-2 text-caption">
                <Chip tone={statusTone(status)}>{exportStatusLabel(status)}</Chip>
                <span className="min-w-0 flex-1">
                  <span className="text-body">{formatDateTime(item.createdAt)}</span>
                  <span className="block text-text-600">
                    {item.sizeBytes != null ? formatBytes(item.sizeBytes) : ""}
                    {item.tables ? ` · ${tables}행` : ""}
                    {item.expiresAt ? ` · ${formatDateTime(item.expiresAt)}까지` : ""}
                    {item.errorCode ? ` · ${ERRORS[item.errorCode] ?? item.errorCode}` : ""}
                  </span>
                </span>
                {status === "READY" ? <DownloadButton item={item} /> : null}
              </li>
            );
          })}
        </ul>
      )}
      {pending ? (
        <Button
          variant="tertiary"
          className="mt-2 h-7 px-2 text-caption"
          onClick={() => router.refresh()}
        >
          상태 새로고침
        </Button>
      ) : null}
    </section>
  );
}
