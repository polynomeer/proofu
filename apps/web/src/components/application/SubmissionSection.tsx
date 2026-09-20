"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { documentTypeLabel, versionAuthorLabel } from "@/lib/labels";

type Submission = Schema<"SubmissionSnapshot">;

/** A version the user could submit, precomputed by the server component. */
export type SubmittableVersion = {
  id: string;
  documentTitle: string;
  documentType: Schema<"DocumentType">;
  label: string | null;
  createdBy: "USER" | "AI";
  createdAt: string;
  pendingApproval: number;
};

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Submissions are frozen (ADR-0006): the list never offers edit or delete. */
export function SubmissionSection({
  applicationId,
  initialItems,
  versions,
  canSubmit,
  isSubmitted,
}: {
  applicationId: string;
  initialItems: Submission[];
  versions: SubmittableVersion[];
  canSubmit: boolean;
  isSubmitted: boolean;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [versionId, setVersionId] = useState(
    versions.find((v) => v.pendingApproval === 0)?.id ?? "",
  );
  const [submittedAt, setSubmittedAt] = useState(() => toLocalInput(new Date()));
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    const { data, error: problem } = await api.POST("/applications/{id}/submissions", {
      params: { path: { id: applicationId } },
      body: {
        documentVersionId: versionId,
        submittedAt: new Date(submittedAt).toISOString(),
        note: note.trim() || undefined,
      },
    });
    setSubmitting(false);
    if (!data) {
      setError(
        problem?.code === "UNSUPPORTED_CLAIM_IN_EXPORT"
          ? "승인되지 않은 근거 없음·추론 문장이 있습니다. 문서 편집기에서 승인한 버전을 저장한 뒤 제출하세요."
          : (problem?.detail ?? "제출 스냅샷을 만들지 못했습니다."),
      );
      return;
    }
    setOpen(false);
    setNote("");
    router.refresh();
  }

  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-section-title">제출</h2>
        {canSubmit && !open ? (
          <Button variant="secondary" onClick={() => setOpen(true)}>
            <Icon name="lock" size={16} />
            {isSubmitted ? "정정 스냅샷" : "제출 기록"}
          </Button>
        ) : null}
      </div>
      {open ? (
        <form
          onSubmit={onSubmit}
          noValidate
          className="mb-4 flex flex-col gap-3 rounded-md border border-primary-600/40 bg-surface-050 p-4"
        >
          {error ? <ErrorState title="제출할 수 없습니다" description={error} /> : null}
          {versions.length === 0 ? (
            <p className="text-body text-text-600">
              저장된 문서 버전이 없습니다. 먼저 문서를 만들고 버전을 저장하세요.
            </p>
          ) : null}
          <Field
            id="submission-version"
            label="제출한 문서 버전"
            required
            help="근거 없음·추론 문장이 모두 승인된 버전만 고를 수 있습니다"
          >
            <select
              id="submission-version"
              className={inputClass}
              value={versionId}
              onChange={(e) => setVersionId(e.target.value)}
            >
              <option value="">선택</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id} disabled={v.pendingApproval > 0}>
                  {documentTypeLabel(v.documentType)} · {v.documentTitle} —{" "}
                  {v.label ?? versionAuthorLabel(v.createdBy)} ({formatDateTime(v.createdAt)})
                  {v.pendingApproval > 0 ? ` · 승인 필요 ${v.pendingApproval}` : ""}
                </option>
              ))}
            </select>
          </Field>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field id="submission-at" label="제출 시각" required>
              <input
                id="submission-at"
                type="datetime-local"
                className={inputClass}
                value={submittedAt}
                max={toLocalInput(new Date())}
                onChange={(e) => setSubmittedAt(e.target.value)}
              />
            </Field>
            <Field id="submission-note" label="메모" help="상태 이력에 남습니다">
              <input
                id="submission-note"
                className={inputClass}
                value={note}
                maxLength={500}
                placeholder="예: 채용 포털로 제출"
                onChange={(e) => setNote(e.target.value)}
              />
            </Field>
          </div>
          <p className="text-caption text-text-600">
            스냅샷은 생성 후 수정하거나 삭제할 수 없습니다. 정정은 새 스냅샷으로만 합니다.
            {!isSubmitted ? " 저장하면 지원 상태가 ‘제출’로 바뀝니다." : ""}
          </p>
          <div className="flex gap-2">
            <Button type="submit" loading={submitting} disabled={!versionId}>
              <Icon name="lock" size={16} />
              스냅샷 고정
            </Button>
            <Button type="button" variant="tertiary" onClick={() => setOpen(false)}>
              취소
            </Button>
          </div>
        </form>
      ) : null}
      {initialItems.length === 0 ? (
        <p className="text-body text-text-600">
          {canSubmit
            ? "아직 제출 기록이 없습니다. 제출한 문서 버전을 고정하면 원천이 바뀌어도 그대로 열 수 있습니다."
            : "제출 기록이 없습니다."}
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border-300">
          {initialItems.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center gap-3 py-3">
              <Chip tone="snapshot">제출 스냅샷</Chip>
              <Link
                href={`/submissions/${s.id}`}
                className="text-body font-semibold hover:underline"
              >
                {s.documentTitle} — {s.versionLabel ?? versionAuthorLabel(s.versionCreatedBy)}
              </Link>
              <span className="text-caption text-text-600 tabular-nums">
                {formatDateTime(s.submittedAt)} · {s.hash.slice(0, 12)}…
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
