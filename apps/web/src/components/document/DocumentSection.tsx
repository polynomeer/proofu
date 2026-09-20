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
import { DOCUMENT_TYPES, documentTypeLabel, versionAuthorLabel } from "@/lib/labels";

type Document = Schema<"Document">;
type DocumentType = Schema<"DocumentType">;

/** Documents of an application (A01 → R01). Creating one opens the editor. */
export function DocumentSection({
  applicationId,
  initialItems,
}: {
  applicationId: string;
  initialItems: Document[];
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<DocumentType>("COVER_LETTER");
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    const { data, error: problem } = await api.POST("/applications/{id}/documents", {
      params: { path: { id: applicationId } },
      body: { type, title: title.trim() || documentTypeLabel(type) },
    });
    setSubmitting(false);
    if (!data) {
      setError(problem?.detail ?? "문서를 만들지 못했습니다.");
      return;
    }
    router.push(`/documents/${data.id}`);
  }

  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-section-title">지원 문서</h2>
        {!open ? (
          <Button variant="secondary" onClick={() => setOpen(true)}>
            <Icon name="plus" size={16} />새 문서
          </Button>
        ) : null}
      </div>
      {open ? (
        <form
          onSubmit={onSubmit}
          noValidate
          className="mb-4 flex flex-col gap-3 rounded-md border border-primary-600/40 bg-surface-050 p-4"
        >
          {error ? <ErrorState title="만들 수 없습니다" description={error} /> : null}
          <div className="grid gap-3 sm:grid-cols-[180px_1fr]">
            <Field id="doc-type" label="유형" required>
              <select
                id="doc-type"
                className={inputClass}
                value={type}
                onChange={(e) => setType(e.target.value as DocumentType)}
              >
                {DOCUMENT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {documentTypeLabel(t)}
                  </option>
                ))}
              </select>
            </Field>
            <Field id="doc-title" label="제목" help="비우면 유형 이름을 씁니다">
              <input
                id="doc-title"
                className={inputClass}
                value={title}
                maxLength={200}
                onChange={(e) => setTitle(e.target.value)}
              />
            </Field>
          </div>
          <div className="flex gap-2">
            <Button type="submit" loading={submitting}>
              만들고 편집
            </Button>
            <Button type="button" variant="tertiary" onClick={() => setOpen(false)}>
              취소
            </Button>
          </div>
        </form>
      ) : null}
      {initialItems.length === 0 ? (
        <p className="text-body text-text-600">
          아직 문서가 없습니다. 공고 매칭에서 후보를 채택한 뒤 문서를 만들면 AI 초안을 생성할 수
          있습니다.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border-300">
          {initialItems.map((d) => (
            <li key={d.id} className="flex flex-wrap items-center gap-3 py-3">
              <Chip tone="neutral">{documentTypeLabel(d.type)}</Chip>
              <Link href={`/documents/${d.id}`} className="text-body font-semibold hover:underline">
                {d.title}
              </Link>
              <span className="text-caption text-text-600">
                {d.latestVersionId
                  ? `최신 버전: ${versionAuthorLabel(d.latestVersionCreatedBy ?? "")}`
                  : "버전 없음"}{" "}
                · {formatDateTime(d.updatedAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
