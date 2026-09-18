import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import type { Schema } from "@proofu/contracts";

import { ClaimStatusChip, SensitivityChip, VerificationChip } from "@/components/evidence/chips";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { DeleteResourceButton } from "@/components/ui/DeleteResourceButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/format";
import { claimTypeLabel, evidenceTypeLabel, relationLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

function sourceHref(source: Schema<"ClaimSource">): string {
  switch (source.type) {
    case "CAREER_ENTRY":
      return `/career/${source.id}`;
    case "PROJECT":
      return `/projects/${source.id}`;
    case "ACHIEVEMENT":
      return source.projectId ? `/projects/${source.projectId}` : "/career";
    default:
      return "/career";
  }
}

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/evidence/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data?.title ?? "Evidence" };
}

export default async function EvidencePage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: evidence } = await api.GET("/evidence/{id}", { params: { path: { id } } });
  if (!evidence) notFound();
  const { data: claims } = await api.GET("/evidence/{id}/claims", { params: { path: { id } } });

  return (
    <>
      <PageHeader
        title={evidence.title}
        description={evidenceTypeLabel(evidence.type)}
        action={
          <div className="flex gap-2">
            <ButtonLink variant="secondary" href={`/evidence/${evidence.id}/edit`}>
              편집
            </ButtonLink>
            <DeleteResourceButton
              resource="evidence"
              id={evidence.id}
              title={evidence.title}
              redirectTo="/evidence"
              note={
                evidence.linkedClaimCount > 0
                  ? `연결된 주장 ${evidence.linkedClaimCount}개는 근거 없음 상태가 됩니다.`
                  : undefined
              }
            />
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-6">
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-section-title">{evidence.type === "NOTE" ? "본문" : "미리보기"}</h2>
            {evidence.uri ? (
              <a
                href={evidence.uri}
                target="_blank"
                rel="noreferrer noopener"
                className="mt-3 block break-all text-body text-primary-600 underline-offset-2 hover:underline"
              >
                {evidence.uri}
              </a>
            ) : null}
            {evidence.body ? (
              <p className="mt-3 text-body whitespace-pre-line">{evidence.body}</p>
            ) : evidence.uri ? (
              <p className="mt-3 text-caption text-text-600">설명이 없습니다.</p>
            ) : null}
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-section-title">연결된 주장</h2>
            {!claims || claims.items.length === 0 ? (
              <EmptyState
                title="아직 연결된 주장이 없습니다"
                description="프로젝트 성과에서 주장을 등록하고 이 근거를 연결하세요."
              />
            ) : (
              <ul className="divide-y divide-border-300 rounded-md border border-border-300 bg-surface-000">
                {claims.items.map((c) => {
                  const link = c.links.find((l) => l.evidenceId === evidence.id);
                  const source = c.sources[0];
                  return (
                    <li key={c.id} className="flex flex-col gap-2 px-4 py-4">
                      <p className="text-body">{c.text}</p>
                      <div className="flex flex-wrap items-center gap-2 text-caption text-text-600">
                        <Chip>{claimTypeLabel(c.type)}</Chip>
                        <ClaimStatusChip value={c.status} />
                        {link ? <Chip>{relationLabel(link.relation)}</Chip> : null}
                        {source ? (
                          <Link
                            href={sourceHref(source)}
                            className="text-primary-600 hover:underline"
                          >
                            {source.title ?? "원천 보기"}
                          </Link>
                        ) : null}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>

        <aside className="flex h-fit flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-card-title">정보</h2>
          <dl className="grid grid-cols-[96px_1fr] gap-x-3 gap-y-2 text-body">
            <dt className="text-text-600">검증 상태</dt>
            <dd>
              <VerificationChip value={evidence.verification} />
            </dd>
            <dt className="text-text-600">민감도</dt>
            <dd>
              <SensitivityChip value={evidence.sensitivity} />
            </dd>
            <dt className="text-text-600">출처</dt>
            <dd>{evidence.source === "USER_INPUT" ? "직접 입력" : evidence.source}</dd>
            <dt className="text-text-600">수집일</dt>
            <dd className="tabular-nums">{formatDate(evidence.capturedAt)}</dd>
            <dt className="text-text-600">연결 주장</dt>
            <dd className="tabular-nums">{evidence.linkedClaimCount}개</dd>
            <dt className="text-text-600">revision</dt>
            <dd className="tabular-nums">{evidence.revision}</dd>
            <dt className="text-text-600">수정</dt>
            <dd className="text-caption text-text-600">{formatDateTime(evidence.updatedAt)}</dd>
          </dl>
        </aside>
      </div>
    </>
  );
}
