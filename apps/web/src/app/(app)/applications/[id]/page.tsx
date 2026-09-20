import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { ApplicationStatusChip } from "@/components/application/chips";
import { DeadlineForm } from "@/components/application/DeadlineForm";
import { ReviewSection } from "@/components/application/ReviewSection";
import {
  SubmissionSection,
  type SubmittableVersion,
} from "@/components/application/SubmissionSection";
import { TransitionPanel } from "@/components/application/TransitionPanel";
import { DocumentSection } from "@/components/document/DocumentSection";
import { ButtonLink } from "@/components/ui/Button";
import { DeleteResourceButton } from "@/components/ui/DeleteResourceButton";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime, formatDday } from "@/lib/format";
import { applicationStatusLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

/** Mirrors ApplicationStatus.acceptsSubmission; the API is the enforcer. */
const SUBMITTABLE: readonly string[] = ["INTERESTED", "PREPARING", "SUBMITTED"];

/** Mirrors ApplicationStatus.acceptsReview for showing the form; the API is the enforcer. */
const REVIEWABLE: readonly string[] = [
  "DOCUMENT_REJECTED",
  "NO_RESPONSE",
  "REVIEW_PENDING",
  "REVIEWED",
];

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/applications/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data ? `${data.roleTitle} · ${data.company}` : "지원" };
}

export default async function ApplicationPage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: a } = await api.GET("/applications/{id}", { params: { path: { id } } });
  if (!a) notFound();
  const [{ data: reviews }, { data: documents }, { data: submissions }] = await Promise.all([
    api.GET("/applications/{id}/reviews", { params: { path: { id } } }),
    api.GET("/applications/{id}/documents", { params: { path: { id } } }),
    api.GET("/applications/{id}/submissions", { params: { path: { id } } }),
  ]);
  const canReview = REVIEWABLE.includes(a.status);
  const canSubmit = SUBMITTABLE.includes(a.status);
  const submittable: SubmittableVersion[] = canSubmit
    ? (
        await Promise.all(
          (documents?.items ?? []).map(async (d) => {
            const { data } = await api.GET("/documents/{id}/versions", {
              params: { path: { id: d.id } },
            });
            return (data?.items ?? []).map((v) => ({
              id: v.id,
              documentTitle: d.title,
              documentType: d.type,
              label: v.label ?? null,
              createdBy: v.createdBy,
              createdAt: v.createdAt,
              pendingApproval: v.blocks.filter(
                (b) => b.certainty !== "SUPPORTED" && !b.approvedByUser,
              ).length,
            }));
          }),
        )
      ).flat()
    : [];

  return (
    <>
      <PageHeader
        title={a.roleTitle}
        description={a.company}
        action={
          <div className="flex gap-2">
            <ButtonLink href={`/applications/${a.id}/matches`}>공고 매칭</ButtonLink>
            <DeleteResourceButton
              resource="application"
              id={a.id}
              title={`${a.company} · ${a.roleTitle}`}
              redirectTo="/applications"
              note="상태 이력은 보존됩니다."
            />
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-6">
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <div className="mb-4 flex flex-wrap items-center gap-3">
              <h2 className="text-section-title">현재 상태</h2>
              <ApplicationStatusChip value={a.status} />
              <span className="text-caption text-text-600">
                {formatDateTime(a.statusChangedAt)}
              </span>
            </div>
            <TransitionPanel
              applicationId={a.id}
              version={a.version}
              allowed={a.allowedTransitions}
            />
          </section>

          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-section-title">상태 이력</h2>
            <ol className="mt-3 flex flex-col">
              {[...a.events].reverse().map((e, i) => (
                <li
                  key={`${e.occurredAt}-${i}`}
                  className="flex gap-3 border-l-2 border-border-300 py-2 pl-4"
                >
                  <span className="w-36 shrink-0 text-caption text-text-600 tabular-nums">
                    {formatDateTime(e.occurredAt)}
                  </span>
                  <span className="min-w-0">
                    <span className="text-body">
                      {e.from ? `${applicationStatusLabel(e.from)} → ` : ""}
                      <span className="font-semibold">{applicationStatusLabel(e.to)}</span>
                    </span>
                    {e.note ? (
                      <span className="mt-0.5 block text-caption text-text-600">{e.note}</span>
                    ) : null}
                  </span>
                </li>
              ))}
            </ol>
          </section>

          <DocumentSection applicationId={a.id} initialItems={documents?.items ?? []} />

          <SubmissionSection
            applicationId={a.id}
            initialItems={submissions?.items ?? []}
            versions={submittable}
            canSubmit={canSubmit}
            isSubmitted={a.status === "SUBMITTED"}
          />

          <ReviewSection
            applicationId={a.id}
            initialReviews={reviews?.items ?? []}
            canReview={canReview}
          />
        </div>

        <aside className="flex h-fit flex-col gap-4">
          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">공고</h2>
            <p className="mt-2 text-body">
              <Link
                href={`/jobs/${a.postingId}?snapshot=${a.snapshotId}`}
                className="text-primary-600 hover:underline"
              >
                {a.company} · {a.roleTitle}
              </Link>
            </p>
            <p className="mt-1 text-caption text-text-600">
              이 지원은 {formatDateTime(a.snapshot.capturedAt)}에 저장된 스냅샷을 기준으로 합니다.
            </p>
            <p className="mt-2 line-clamp-4 text-caption text-text-600">{a.snapshot.textPreview}</p>
          </div>
          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">마감</h2>
            {a.deadlineAt ? (
              <p className="mt-2 text-body tabular-nums">
                <span className="font-semibold">{formatDday(a.deadlineAt)}</span> ·{" "}
                {formatDateTime(a.deadlineAt)}
              </p>
            ) : (
              <p className="mt-2 text-caption text-text-600">마감일이 없습니다.</p>
            )}
            <div className="mt-3">
              <DeadlineForm applicationId={a.id} version={a.version} deadlineAt={a.deadlineAt} />
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}
