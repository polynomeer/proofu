import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { Chip } from "@/components/ui/Chip";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { certaintyLabel, documentTypeLabel, versionAuthorLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/submission-snapshots/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data ? `제출 스냅샷 · ${data.documentTitle}` : "제출 스냅샷" };
}

/** US06: the document exactly as submitted. Everything here is frozen — no actions. */
export default async function SubmissionPage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: s } = await api.GET("/submission-snapshots/{id}", { params: { path: { id } } });
  if (!s) notFound();

  const titles = new Map(s.sections.map((sec) => [sec.id, sec.title]));
  const sections = new Map<string, typeof s.version.blocks>();
  for (const sec of s.sections) sections.set(sec.id, []);
  for (const b of s.version.blocks) {
    const key = b.blockId.slice(0, b.blockId.lastIndexOf("-")) || b.blockId;
    sections.set(key, [...(sections.get(key) ?? []), b]);
  }

  return (
    <>
      <PageHeader
        back={{ href: `/applications/${s.applicationId}`, label: "지원 상세" }}
        title={s.documentTitle}
        description={`${s.company} · ${s.roleTitle} · 제출 ${formatDateTime(s.submittedAt)}`}
        action={<Chip tone="snapshot">제출 스냅샷 · 변경 불가</Chip>}
      />
      <p className="mb-4 flex flex-wrap items-center gap-3 text-caption text-text-600">
        <Chip tone="neutral">{documentTypeLabel(s.documentType)}</Chip>
        <span>
          버전 {s.versionLabel ?? versionAuthorLabel(s.versionCreatedBy)} ·{" "}
          {formatDateTime(s.version.createdAt)} · 템플릿 {s.version.templateVersion}
        </span>
        <span className="font-mono">해시 {s.hash.slice(0, 16)}…</span>
      </p>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <article className="flex flex-col gap-4">
          {[...sections.entries()]
            .filter(([, blocks]) => blocks.length > 0)
            .map(([section, blocks]) => (
              <section
                key={section}
                className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6"
              >
                <h2 className="text-card-title">{titles.get(section) ?? section}</h2>
                <ol className="mt-3 flex flex-col gap-3">
                  {blocks.map((b) => (
                    <li key={b.blockId} className="flex flex-col gap-1">
                      <p className="text-body whitespace-pre-line">{b.text}</p>
                      <span className="text-caption text-text-600">
                        {certaintyLabel(b.certainty)}
                        {b.approvedByUser ? " · 승인됨" : ""} · 주장 {b.claimRefs?.length ?? 0} ·
                        Evidence {b.evidenceRefs?.length ?? 0}
                      </span>
                    </li>
                  ))}
                </ol>
              </section>
            ))}
        </article>
        <aside className="flex h-fit flex-col gap-4">
          <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
            <h2 className="text-card-title">고정된 참조</h2>
            <dl className="mt-2 grid grid-cols-[96px_1fr] gap-x-3 gap-y-2 text-caption">
              <dt className="text-text-600">문서 버전</dt>
              <dd>
                <Link
                  href={`/documents/${s.documentId}`}
                  className="text-primary-600 hover:underline"
                >
                  {s.documentTitle}
                </Link>
                <span className="block font-mono text-text-600">{s.documentVersionId}</span>
              </dd>
              <dt className="text-text-600">공고 스냅샷</dt>
              <dd className="font-mono break-all">{s.postingSnapshotId}</dd>
              <dt className="text-text-600">해시</dt>
              <dd className="font-mono break-all">{s.hash}</dd>
              <dt className="text-text-600">기록</dt>
              <dd>{formatDateTime(s.createdAt)}</dd>
            </dl>
          </div>
          <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
            <h2 className="text-card-title">출처 (provenance)</h2>
            <ul className="mt-2 flex flex-col gap-1 text-caption">
              {(s.version.provenance ?? []).length === 0 ? (
                <li className="text-text-600">없음</li>
              ) : (
                (s.version.provenance ?? []).map((p) => (
                  <li
                    key={`${p.blockId}-${p.sourceType}-${p.sourceId}-${p.relation}`}
                    className="tabular-nums"
                  >
                    <span className="font-semibold">{p.blockId}</span> ← {p.sourceType} r
                    {p.sourceRevision} ({p.relation})
                  </li>
                ))
              )}
            </ul>
          </div>
        </aside>
      </div>
    </>
  );
}
