"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { applicationStatusLabel } from "@/lib/labels";

type Status = Schema<"ApplicationStatus">;

/** Statuses that mean "bad news" get the secondary style so the happy path is the visible primary. */
const SECONDARY: readonly Status[] = ["WITHDRAWN", "DOCUMENT_REJECTED", "NO_RESPONSE"];

/**
 * Transition buttons come straight from the server's allowedTransitions — the UI never
 * holds its own copy of the state machine (docs/domain/application-lifecycle.md).
 */
export function TransitionPanel({
  applicationId,
  version,
  allowed,
}: {
  applicationId: string;
  version: number;
  allowed: Status[];
}) {
  const router = useRouter();
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState<Status | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  async function move(to: Status) {
    setBusy(to);
    setProblem(null);
    const result = await api.POST("/applications/{id}/transitions", {
      params: { path: { id: applicationId } },
      body: { to, version, note: note || null },
    });
    setBusy(null);
    if (result.error) {
      setProblem(
        result.error.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 먼저 변경되었습니다. 새로고침 후 다시 시도하세요."
          : result.error.code === "INVALID_STATUS_TRANSITION"
            ? "현재 상태에서는 허용되지 않는 전이입니다. 새로고침 후 다시 확인하세요."
            : (result.error.detail ?? "상태를 바꾸지 못했습니다."),
      );
      return;
    }
    setNote("");
    router.refresh();
  }

  if (allowed.length === 0) {
    return (
      <p className="text-body text-text-600">
        더 이상 바꿀 수 있는 상태가 없습니다. 면접 단계는 iterview에서 관리됩니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {problem ? <ErrorState title="상태를 바꿀 수 없습니다" description={problem} /> : null}
      <Field id="transition-note" label="메모" help="결과 통보 시각, 채널 등 확인한 사실만">
        <input
          id="transition-note"
          className={inputClass}
          maxLength={500}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </Field>
      <div className="flex flex-wrap gap-2">
        {allowed.map((to) => (
          <Button
            key={to}
            variant={SECONDARY.includes(to) ? "secondary" : "primary"}
            onClick={() => move(to)}
            loading={busy === to}
            disabled={busy !== null && busy !== to}
          >
            → {applicationStatusLabel(to)}
          </Button>
        ))}
      </div>
    </div>
  );
}
