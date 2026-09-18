"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

/** Starts an application from a posting's snapshot; the application pins that snapshot. */
export function CreateApplicationButton({
  snapshotId,
  disabled,
}: {
  snapshotId?: string;
  disabled?: boolean;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function onClick() {
    if (!snapshotId) return;
    setBusy(true);
    const result = await api.POST("/applications", { body: { snapshotId } });
    setBusy(false);
    if (result.error) {
      window.alert(result.error.detail ?? "지원을 만들지 못했습니다.");
      return;
    }
    router.push(`/applications/${result.data.id}`);
    router.refresh();
  }

  return (
    <Button
      onClick={onClick}
      loading={busy}
      disabled={disabled || !snapshotId}
      title={!snapshotId ? "저장된 본문이 필요합니다" : undefined}
    >
      <Icon name="kanban" size={16} />
      지원 만들기
    </Button>
  );
}
