import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { CreateApplicationButton } from "@/components/application/CreateApplicationButton";
import { AnalyzeRequirementsButton } from "@/components/jobs/AnalyzeRequirementsButton";
import { SnapshotRequirementsPanel } from "@/components/jobs/SnapshotRequirementsPanel";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { DeleteResourceButton } from "@/components/ui/DeleteResourceButton";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { snapshotSourceLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;
type Search = Promise<{ snapshot?: string; imported?: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/job-postings/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data ? `${data.roleTitle} · ${data.company}` : "채용공고" };
}

export default async function JobPostingPage({
  params,
  searchParams,
}: {
  params: Params;
  searchParams: Search;
}) {
  const { id } = await params;
  const { snapshot: selectedId, imported } = await searchParams;
  const { data: posting } = await api.GET("/job-postings/{id}", { params: { path: { id } } });
  if (!posting) notFound();

  const selectedSummary =
    posting.snapshots.find((s) => s.id === selectedId) ?? posting.snapshots[0];
  const [{ data: snapshot }, { data: requirements }] = selectedSummary
    ? await Promise.all([
        api.GET("/job-posting-snapshots/{id}", { params: { path: { id: selectedSummary.id } } }),
        api.GET("/job-posting-snapshots/{id}/requirements", {
          params: { path: { id: selectedSummary.id } },
        }),
      ])
    : [{ data: undefined }, { data: undefined }];
  const isLatest = selectedSummary?.id === posting.snapshots[0]?.id;

  return (
    <>
      <PageHeader
        title={posting.roleTitle}
        description={posting.company}
        action={
          <div className="flex flex-wrap gap-2">
            <CreateApplicationButton snapshotId={posting.snapshots[0]?.id} />
            <AnalyzeRequirementsButton snapshotId={selectedSummary?.id} />
            <ButtonLink variant="secondary" href={`/jobs/new?postingId=${posting.id}`}>
              <Icon name="plus" size={16} />새 버전 붙여넣기
            </ButtonLink>
            <DeleteResourceButton
              resource="job-posting"
              id={posting.id}
              title={`${posting.company} · ${posting.roleTitle}`}
              redirectTo="/jobs"
              note="저장된 스냅샷 원문은 삭제되지 않고 보존됩니다."
            />
          </div>
        }
      />

      {imported === "same" ? (
        <p
          role="status"
          className="mb-4 rounded-md border border-border-300 bg-surface-050 px-4 py-3 text-body"
        >
          붙여넣은 내용이 이미 저장된 버전과 같아 기존 스냅샷을 그대로 유지했습니다.
        </p>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-4">
          {selectedSummary ? (
            <div className="flex flex-wrap items-center gap-2 text-caption text-text-600">
              <Chip tone="snapshot">{isLatest ? "최신 스냅샷" : "이전 스냅샷"}</Chip>
              {formatDateTime(selectedSummary.capturedAt)}
            </div>
          ) : null}
          {snapshot ? (
            <SnapshotRequirementsPanel
              key={`${snapshot.id}:${(requirements?.items ?? []).map((r) => `${r.id}.${r.version}`).join(",")}`}
              snapshotId={snapshot.id}
              rawText={snapshot.rawText}
              initialItems={requirements?.items ?? []}
            />
          ) : (
            <p className="text-body text-text-600">저장된 본문이 없습니다.</p>
          )}
        </div>

        <aside className="flex h-fit flex-col gap-4">
          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">정보</h2>
            <dl className="mt-3 grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 text-body">
              <dt className="text-text-600">URL</dt>
              <dd className="break-all">
                {posting.canonicalUrl ? (
                  <a
                    href={posting.canonicalUrl}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="text-primary-600 underline-offset-2 hover:underline"
                  >
                    {posting.canonicalUrl}
                  </a>
                ) : (
                  <span className="text-text-600">없음</span>
                )}
              </dd>
              <dt className="text-text-600">스냅샷</dt>
              <dd className="tabular-nums">{posting.snapshotCount}개</dd>
              <dt className="text-text-600">저장</dt>
              <dd className="text-caption text-text-600">{formatDateTime(posting.createdAt)}</dd>
            </dl>
          </div>

          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">버전 기록</h2>
            <p className="mt-1 text-caption text-text-600">
              내용이 바뀔 때만 새 버전이 생깁니다. 각 버전은 수정할 수 없습니다.
            </p>
            <ol className="mt-3 flex flex-col gap-1">
              {posting.snapshots.map((s, i) => {
                const active = s.id === selectedSummary?.id;
                return (
                  <li key={s.id}>
                    <Link
                      href={`/jobs/${posting.id}?snapshot=${s.id}`}
                      aria-current={active ? "true" : undefined}
                      className={[
                        "flex flex-col gap-0.5 rounded-md border px-3 py-2 text-caption",
                        active
                          ? "border-primary-600 bg-primary-050"
                          : "border-border-300 hover:bg-surface-050",
                      ].join(" ")}
                    >
                      <span className="flex items-center justify-between font-semibold text-text-900 tabular-nums">
                        v{posting.snapshots.length - i}
                        <span className="font-normal text-text-600">
                          {formatDateTime(s.capturedAt)}
                        </span>
                      </span>
                      <span className="flex items-center gap-2 text-text-600">
                        <span>{snapshotSourceLabel(s.source)}</span>
                        <span className="font-mono">{s.contentHash.slice(0, 12)}</span>
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ol>
          </div>
        </aside>
      </div>
    </>
  );
}
