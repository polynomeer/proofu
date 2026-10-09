import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { AtsPanel } from "@/components/document/AtsPanel";
import { DocumentEditor } from "@/components/document/DocumentEditor";
import { ExportPanel } from "@/components/document/ExportPanel";
import { GenerateDraftButton } from "@/components/document/GenerateDraftButton";
import { Chip } from "@/components/ui/Chip";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { documentTypeLabel, versionAuthorLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/documents/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data?.title ?? "문서" };
}

/** R01: structure on the left, canvas in the middle, versions and lineage on the right. */
export default async function DocumentPage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: doc } = await api.GET("/documents/{id}", { params: { path: { id } } });
  if (!doc) notFound();
  const [{ data: application }, { data: versions }, { data: matches }] = await Promise.all([
    api.GET("/applications/{id}", { params: { path: { id: doc.applicationId } } }),
    api.GET("/documents/{id}/versions", { params: { path: { id } } }),
    api.GET("/applications/{id}/matches", { params: { path: { id: doc.applicationId } } }),
  ]);
  const accepted =
    matches?.groups.flatMap((g) => g.candidates.filter((c) => c.userDecision === "ACCEPTED")) ?? [];
  const latest = doc.latestVersion ?? null;
  const blocks = latest?.blocks ?? [];
  const [{ data: exports }, { data: ats }] = latest
    ? await Promise.all([
        api.GET("/document-versions/{id}/exports", { params: { path: { id: latest.id } } }),
        api.GET("/document-versions/{id}/ats-check", { params: { path: { id: latest.id } } }),
      ])
    : [{ data: undefined }, { data: undefined }];

  return (
    <>
      <PageHeader
        back={{ href: `/applications/${doc.applicationId}`, label: "지원 상세" }}
        title={doc.title}
        description={application ? `${application.company} · ${application.roleTitle}` : undefined}
        action={
          <GenerateDraftButton
            documentId={doc.id}
            hasVersion={latest !== null}
            hasAcceptedSources={accepted.length > 0}
          />
        }
      />
      <p className="mb-4 flex flex-wrap items-center gap-3 text-caption text-text-600">
        <Chip tone="neutral">{documentTypeLabel(doc.type)}</Chip>
        {latest ? (
          <span>
            최신 버전 {versionAuthorLabel(latest.createdBy)} · {formatDateTime(latest.createdAt)}
          </span>
        ) : (
          <span>아직 버전이 없습니다.</span>
        )}
        <span>
          AI는 채택한 주장만 문장으로 배열합니다. 근거 없음·추론 문장은 승인해야 내보낼 수 있습니다.
        </span>
      </p>

      <div className="grid gap-4 lg:grid-cols-[240px_minmax(0,1fr)] 2xl:grid-cols-[240px_minmax(0,1fr)_320px]">
        <nav
          aria-label="문서 구조"
          className="flex h-fit flex-col gap-1 rounded-md border border-border-300 bg-surface-000 p-3 lg:sticky lg:top-20"
        >
          <h2 className="px-2 pb-1 text-caption font-semibold text-text-600">문서 구조</h2>
          <ol className="flex flex-col gap-0.5 text-body">
            {doc.sections.map((s) => {
              const own = blocks.filter((b) => b.blockId.startsWith(`${s.id}-`));
              const pending = own.filter(
                (b) => b.certainty !== "SUPPORTED" && !b.approvedByUser,
              ).length;
              return (
                <li key={s.id}>
                  <a
                    href={`#section-${s.id}`}
                    className="flex min-h-10 items-center justify-between gap-2 rounded-md px-2 hover:bg-surface-050"
                  >
                    <span>{s.title}</span>
                    {pending > 0 ? (
                      <Chip tone="review">미승인 {pending}</Chip>
                    ) : (
                      <span className="text-caption text-text-600 tabular-nums">{own.length}</span>
                    )}
                  </a>
                </li>
              );
            })}
          </ol>
          {doc.pendingApprovalCount > 0 ? (
            <p className="mt-3 rounded-md bg-surface-050 p-3 text-caption text-text-600">
              <span className="block font-semibold text-text-900">내보내기 전 확인</span>
              근거 없음·추론 문장 {doc.pendingApprovalCount}개를 승인하거나 고쳐야 내보낼 수
              있습니다.
            </p>
          ) : null}
        </nav>

        <div className="min-w-0">
          {latest === null && accepted.length === 0 ? (
            <div className="mb-4 rounded-md border border-warning-600/40 bg-warning-050 p-4 text-body">
              채택한 매칭 후보가 없습니다.{" "}
              <Link
                href={`/applications/${doc.applicationId}/matches`}
                className="text-primary-600 hover:underline"
              >
                공고 매칭
              </Link>
              에서 근거로 쓸 주장을 채택하면 AI 초안을 생성할 수 있습니다. 직접 문장을 써서 저장할
              수도 있습니다.
            </div>
          ) : null}
          <DocumentEditor
            key={latest?.id ?? "empty"}
            documentId={doc.id}
            parentVersionId={latest?.id ?? null}
            sections={doc.sections}
            initialBlocks={blocks}
          />
        </div>

        <aside className="grid h-fit gap-4 md:grid-cols-2 lg:col-span-2 2xl:col-span-1 2xl:grid-cols-1">
          <div className="rounded-md border border-border-300 bg-surface-000 p-4">
            <h2 className="text-card-title">근거로 채택한 주장</h2>
            {accepted.length === 0 ? (
              <p className="mt-2 text-caption text-text-600">없음</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-2 text-caption">
                {accepted.map((c) => (
                  <li key={c.id}>
                    <span className="text-body">{c.claimText}</span>
                    <span className="block text-text-600">
                      Evidence {c.evidence.length}건 · {c.score}점
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <Link
              href={`/applications/${doc.applicationId}/matches`}
              className="mt-2 inline-block text-caption text-primary-600 hover:underline"
            >
              공고 매칭에서 바꾸기
            </Link>
          </div>
          <AtsPanel report={ats ?? null} />
          <ExportPanel
            versionId={latest?.id ?? null}
            pendingApproval={doc.pendingApprovalCount}
            initialItems={exports?.items ?? []}
          />
          <div className="rounded-md border border-border-300 bg-surface-000 p-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-card-title">버전 기록</h2>
              {(versions?.items.length ?? 0) >= 2 ? (
                <Link
                  href={`/documents/${doc.id}/compare`}
                  className="text-caption text-primary-600 hover:underline"
                >
                  비교
                </Link>
              ) : null}
            </div>
            {!versions || versions.items.length === 0 ? (
              <p className="mt-2 text-caption text-text-600">아직 저장된 버전이 없습니다.</p>
            ) : (
              <ol className="mt-2 flex flex-col gap-2">
                {versions.items.map((v) => (
                  <li key={v.id} className="border-l-2 border-border-300 pl-3">
                    <span className="text-body">
                      {v.label ?? versionAuthorLabel(v.createdBy)}
                      {v.id === latest?.id ? (
                        <span className="ml-2 text-caption text-primary-700">최신</span>
                      ) : latest ? (
                        <Link
                          href={`/documents/${doc.id}/compare?base=${v.id}&target=${latest.id}`}
                          className="ml-2 text-caption text-primary-600 hover:underline"
                        >
                          최신과 비교
                        </Link>
                      ) : null}
                    </span>
                    <span className="block text-caption text-text-600">
                      {versionAuthorLabel(v.createdBy)} · {formatDateTime(v.createdAt)} · 블록{" "}
                      {v.blocks.length} · 출처 {v.provenance?.length ?? 0}
                      {v.modelRef ? ` · ${v.modelRef}` : ""}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
          {latest ? (
            <div className="rounded-md border border-border-300 bg-surface-000 p-4">
              <h2 className="text-card-title">출처 (provenance)</h2>
              <p className="mt-1 text-caption text-text-600">
                최신 버전의 블록이 어느 원천 revision에서 나왔는지. 생성 후 바뀌지 않습니다.
              </p>
              <ul className="mt-2 flex flex-col gap-1 text-caption">
                {(latest.provenance ?? []).map((p) => (
                  <li
                    key={`${p.blockId}-${p.sourceType}-${p.sourceId}-${p.relation}`}
                    className="tabular-nums"
                  >
                    <span className="font-semibold">{p.blockId}</span> ← {p.sourceType} r
                    {p.sourceRevision} ({p.relation})
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </aside>
      </div>
    </>
  );
}
