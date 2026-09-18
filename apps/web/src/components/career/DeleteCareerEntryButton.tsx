"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";

/** Danger action with an explicit confirmation naming the target (docs/design/components.md §삭제와 복구). */
export function DeleteCareerEntryButton({ id, title }: { id: string; title: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function onClick() {
    if (
      !window.confirm(
        `"${title}" 항목을 삭제할까요?\n30일 동안 휴지통에 보관된 뒤 영구 삭제됩니다.`,
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/career-entries/{id}", { params: { path: { id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    router.push("/career");
    router.refresh();
  }

  return (
    <Button variant="danger" onClick={onClick} loading={busy}>
      삭제
    </Button>
  );
}
