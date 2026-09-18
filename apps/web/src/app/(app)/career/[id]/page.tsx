import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { DeleteCareerEntryButton } from "@/components/career/DeleteCareerEntryButton";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime, formatPeriod } from "@/lib/format";
import { careerEntryTypeLabel, visibilityLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/career-entries/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data?.title ?? "커리어 상세" };
}

export default async function CareerEntryPage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: entry } = await api.GET("/career-entries/{id}", { params: { path: { id } } });
  if (!entry) notFound();

  return (
    <>
      <PageHeader
        title={entry.title}
        description={[entry.organization, entry.location].filter(Boolean).join(" · ")}
        action={
          <div className="flex gap-2">
            <ButtonLink variant="secondary" href={`/career/${entry.id}/edit`}>
              편집
            </ButtonLink>
            <DeleteCareerEntryButton id={entry.id} title={entry.title} />
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <section className="rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-section-title">설명</h2>
          {entry.description ? (
            <p className="mt-3 text-body whitespace-pre-line">{entry.description}</p>
          ) : (
            <p className="mt-3 text-body text-text-600">
              설명이 없습니다. 편집에서 역할과 책임을 추가하세요.
            </p>
          )}
        </section>

        <aside className="flex flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-card-title">정보</h2>
          <dl className="grid grid-cols-[96px_1fr] gap-x-3 gap-y-2 text-body">
            <dt className="text-text-600">유형</dt>
            <dd>
              <Chip>{careerEntryTypeLabel(entry.type)}</Chip>
            </dd>
            <dt className="text-text-600">기간</dt>
            <dd className="tabular-nums">{formatPeriod(entry.startDate, entry.endDate)}</dd>
            <dt className="text-text-600">공개 범위</dt>
            <dd>
              <Chip tone={entry.visibility === "PRIVATE" ? "private" : "neutral"}>
                {visibilityLabel(entry.visibility)}
              </Chip>
            </dd>
            <dt className="text-text-600">revision</dt>
            <dd className="tabular-nums">{entry.revision}</dd>
            <dt className="text-text-600">수정</dt>
            <dd className="text-caption text-text-600">{formatDateTime(entry.updatedAt)}</dd>
            <dt className="text-text-600">생성</dt>
            <dd className="text-caption text-text-600">{formatDateTime(entry.createdAt)}</dd>
          </dl>
        </aside>
      </div>
    </>
  );
}
