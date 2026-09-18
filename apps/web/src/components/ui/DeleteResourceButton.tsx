"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";

type Resource = "career-entry" | "project" | "evidence";

const DELETE: Record<Resource, (id: string) => Promise<{ error?: { detail?: string } }>> = {
  "career-entry": (id) => api.DELETE("/career-entries/{id}", { params: { path: { id } } }),
  project: (id) => api.DELETE("/projects/{id}", { params: { path: { id } } }),
  evidence: (id) => api.DELETE("/evidence/{id}", { params: { path: { id } } }),
};

/** Danger action with an explicit confirmation naming the target (docs/design/components.md §삭제와 복구). */
export function DeleteResourceButton({
  resource,
  id,
  title,
  redirectTo,
  note,
}: {
  resource: Resource;
  id: string;
  title: string;
  redirectTo: string;
  /** Extra consequence to state in the confirmation, e.g. child records that go to the trash too. */
  note?: string;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function onClick() {
    const lines = [
      `"${title}" 항목을 삭제할까요?`,
      note,
      "30일 동안 휴지통에 보관된 뒤 영구 삭제됩니다.",
    ];
    if (!window.confirm(lines.filter(Boolean).join("\n"))) return;
    setBusy(true);
    const { error } = await DELETE[resource](id);
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    router.push(redirectTo);
    router.refresh();
  }

  return (
    <Button variant="danger" onClick={onClick} loading={busy}>
      삭제
    </Button>
  );
}
