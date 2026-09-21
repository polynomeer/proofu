import type { Metadata } from "next";
import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { ApplicationStatusChip } from "@/components/application/chips";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { DOCUMENT_TYPES, documentTypeLabel, versionAuthorLabel } from "@/lib/labels";

export const metadata: Metadata = { title: "지원 문서" };
export const dynamic = "force-dynamic";

type SearchParams = Promise<{ type?: string; q?: string; cursor?: string }>;
type Document = Schema<"Document">;

function asType(value?: string) {
  return DOCUMENT_TYPES.find((t) => t === value);
}

function href(params: { type?: string; q?: string; cursor?: string }) {
  const p = new URLSearchParams();
  if (params.type) p.set("type", params.type);
  if (params.q) p.set("q", params.q);
  if (params.cursor) p.set("cursor", params.cursor);
  const s = p.toString();
  return s ? `/documents?${s}` : "/documents";
}

function Row({ d }: { d: Document }) {
  return (
    <li>
      <Link
        href={`/documents/${d.id}`}
        className="grid gap-2 rounded-md border border-border-300 bg-surface-000 p-4 hover:bg-surface-050 sm:grid-cols-[1fr_auto] sm:items-center"
      >
        <span className="flex min-w-0 flex-col gap-1">
          <span className="flex flex-wrap items-center gap-2">
            <Chip tone="neutral">{documentTypeLabel(d.type)}</Chip>
            <span className="text-card-title">{d.title}</span>
          </span>
          <span className="text-body text-text-600">
            {d.company} · {d.roleTitle}
          </span>
        </span>
        <span className="flex flex-wrap items-center gap-2 text-caption text-text-600 sm:justify-end">
          {d.applicationStatus ? <ApplicationStatusChip value={d.applicationStatus} /> : null}
          {(d.submissionCount ?? 0) > 0 ? (
            <Chip tone="snapshot">제출 {d.submissionCount}</Chip>
          ) : null}
          <span>
            {d.latestVersionId
              ? `최신 ${versionAuthorLabel(d.latestVersionCreatedBy ?? "")}`
              : "버전 없음"}{" "}
            · {formatDateTime(d.updatedAt)}
          </span>
        </span>
      </Link>
    </li>
  );
}

/** IA "지원 문서": every document in the workspace; new ones are created from an application. */
export default async function DocumentsPage({ searchParams }: { searchParams: SearchParams }) {
  const { type, q, cursor } = await searchParams;
  const typeFilter = asType(type);
  const result = await api
    .GET("/documents", { params: { query: { type: typeFilter, q, cursor, limit: 20 } } })
    .catch((cause: unknown) => ({ data: undefined, error: undefined, cause }));

  const options: { value?: string; label: string }[] = [
    { label: "전체" },
    ...DOCUMENT_TYPES.map((t) => ({ value: t, label: documentTypeLabel(t) })),
  ];

  return (
    <>
      <PageHeader
        title="지원 문서"
        description="문서는 지원 건에 속합니다. 채택한 근거로 AI 초안을 만들고, 승인한 버전만 내보내거나 제출합니다."
        action={<ButtonLink href="/applications">지원 건에서 문서 만들기</ButtonLink>}
      />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <nav aria-label="유형 필터" className="flex flex-wrap gap-2">
          {options.map(({ value, label }) => {
            const active = (typeFilter ?? undefined) === value;
            return (
              <Link
                key={label}
                href={href({ type: value, q })}
                aria-current={active ? "true" : undefined}
                className={[
                  "inline-flex h-9 items-center rounded-md border px-3 text-body",
                  active
                    ? "border-primary-600 bg-primary-600 font-semibold text-white"
                    : "border-border-300 bg-surface-000 text-text-900 hover:bg-surface-050",
                ].join(" ")}
              >
                {label}
              </Link>
            );
          })}
        </nav>
        <form action="/documents" method="get" className="flex items-end gap-2">
          {typeFilter ? <input type="hidden" name="type" value={typeFilter} /> : null}
          <Field id="documents-q" label="검색" help="제목, 회사, 직무">
            <input
              id="documents-q"
              name="q"
              defaultValue={q ?? ""}
              className={`${inputClass} w-64`}
            />
          </Field>
        </form>
      </div>

      {!result.data ? (
        <ErrorState
          title="문서 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : result.data.items.length === 0 ? (
        <EmptyState
          title={typeFilter || q ? "조건에 맞는 문서가 없습니다" : "아직 문서가 없습니다"}
          description={
            typeFilter || q
              ? "다른 유형을 선택하거나 검색어를 바꿔 보세요."
              : "지원 관리에서 지원 건을 열고 ‘새 문서’를 만들면 여기에 모입니다."
          }
          action={
            typeFilter || q ? undefined : (
              <ButtonLink href="/applications">지원 관리로 이동</ButtonLink>
            )
          }
        />
      ) : (
        <>
          <ul className="flex flex-col gap-2">
            {result.data.items.map((d) => (
              <Row key={d.id} d={d} />
            ))}
          </ul>
          {result.data.nextCursor ? (
            <div className="mt-4 flex justify-center">
              <ButtonLink
                variant="secondary"
                href={href({ type: typeFilter, q, cursor: result.data.nextCursor })}
              >
                다음 페이지
              </ButtonLink>
            </div>
          ) : null}
        </>
      )}
    </>
  );
}
