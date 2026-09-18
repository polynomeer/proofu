"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/Button";
import { inputClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { toDateInput } from "@/lib/format";

export function DeadlineForm({
  applicationId,
  version,
  deadlineAt,
}: {
  applicationId: string;
  version: number;
  deadlineAt: string | null | undefined;
}) {
  const router = useRouter();
  const [value, setValue] = useState(deadlineAt ? toDateInput(deadlineAt) : "");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    const result = await api.PATCH("/applications/{id}", {
      params: { path: { id: applicationId } },
      body: { deadlineAt: value ? new Date(`${value}T23:59:00`).toISOString() : null, version },
    });
    setBusy(false);
    if (result.error) {
      window.alert(result.error.detail ?? "마감일을 저장하지 못했습니다.");
      return;
    }
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="flex items-end gap-2">
      <label className="flex flex-1 flex-col gap-1 text-caption text-text-600">
        마감일
        <input
          type="date"
          className={inputClass}
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </label>
      <Button type="submit" variant="secondary" loading={busy}>
        저장
      </Button>
    </form>
  );
}
