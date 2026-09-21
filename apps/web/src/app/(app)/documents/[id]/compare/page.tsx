import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { certaintyLabel, versionAuthorLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;
type SearchParams = Promise<{ base?: string; target?: string }>;
type Version = Schema<"DocumentVersion">;
type Entry = Schema<"BlockDiff">;

export const metadata: Metadata = { title: "버전 비교" };

function versionLabel(v: Version) {
  return `${v.label ?? versionAuthorLabel(v.createdBy)} · ${formatDateTime(v.createdAt)}`;
}

const KIND: Record<
  Entry["kind"],
  { label: string; tone: "verified" | "expired" | "review" | "neutral" }
> = {
  ADDED: { label: "추가", tone: "verified" },
  REMOVED: { label: "삭제", tone: "expired" },
  CHANGED: { label: "변경", tone: "review" },
  UNCHANGED: { label: "동일", tone: "neutral" },
};

const FIELD: Record<string, string> = {
  text: "문장",
  certainty: "확신도",
  approval: "승인",
  references: "참조",
};

function TextDiff({ segments }: { segments: Entry["textDiff"] }) {
  return (
    <p className="text-body whitespace-pre-line">
      {segments.map((s, i) =>
        s.kind === "SAME" ? (
          <span key={i}>{s.text}</span>
        ) : s.kind === "ADDED" ? (
          <ins key={i} className="rounded-sm bg-success-050 text-success-700 no-underline">
            {s.text}
          </ins>
        ) : (
          <del key={i} className="rounded-sm bg-warning-050 text-warning-700">
            {s.text}
          </del>
        ),
      )}
    </p>
  );
}

/** R02 "비교": two versions side by side at block level, word-level marks inside changed text. */
export default async function ComparePage({
  params,
  searchParams,
}: {
  params: Params;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const { base, target } = await searchParams;
  const [{ data: doc }, { data: versions }] = await Promise.all([
    api.GET("/documents/{id}", { params: { path: { id } } }),
    api.GET("/documents/{id}/versions", { params: { path: { id } } }),
  ]);
  if (!doc || !versions) notFound();
  const items = versions.items; // newest first
  const targetId = target && items.some((v) => v.id === target) ? target : items[0]?.id;
  const baseId =
    base && items.some((v) => v.id === base)
      ? base
      : (items.find((v) => v.id === (items.find((t) => t.id === targetId)?.parentId ?? ""))?.id ??
        items[1]?.id);
  const diff =
    targetId && baseId && targetId !== baseId
      ? (
          await api.GET("/document-versions/{id}/diff", {
            params: { path: { id: targetId }, query: { against: baseId } },
          })
        ).data
      : undefined;

  return (
    <>
      <PageHeader title="버전 비교" description={doc.title} />
      <p className="mb-4 text-caption text-text-600">
        <Link href={`/documents/${doc.id}`} className="text-primary-600 hover:underline">
          ← 문서 편집
        </Link>
        <span className="ml-3">
          블록 id 기준으로 추가·삭제·변경을 표시합니다. 문장 안의 단어 단위 표시는 삭제(취소선)와
          추가(강조)입니다.
        </span>
      </p>

      {items.length < 2 ? (
        <p className="rounded-md border border-border-300 bg-surface-000 p-6 text-body text-text-600">
          비교하려면 버전이 두 개 이상 필요합니다.
        </p>
      ) : (
        <>
          <form
            method="get"
            className="mb-4 grid gap-3 rounded-md border border-border-300 bg-surface-000 p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
          >
            <label className="flex flex-col gap-1 text-caption">
              <span className="font-semibold">기준 (이전)</span>
              <select
                name="base"
                defaultValue={baseId}
                className="h-10 rounded-md border border-border-300 bg-surface-000 px-3 text-body"
              >
                {items.map((v) => (
                  <option key={v.id} value={v.id}>
                    {versionLabel(v)}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-caption">
              <span className="font-semibold">대상 (이후)</span>
              <select
                name="target"
                defaultValue={targetId}
                className="h-10 rounded-md border border-border-300 bg-surface-000 px-3 text-body"
              >
                {items.map((v) => (
                  <option key={v.id} value={v.id}>
                    {versionLabel(v)}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              className="inline-flex h-10 items-center justify-center rounded-md bg-primary-600 px-4 text-body font-semibold text-white hover:bg-primary-700"
            >
              비교
            </button>
          </form>

          {!diff ? (
            <p className="text-body text-text-600">서로 다른 두 버전을 고르세요.</p>
          ) : (
            <>
              <p className="mb-3 flex flex-wrap gap-2 text-caption">
                <Chip tone="verified">추가 {diff.added}</Chip>
                <Chip tone="expired">삭제 {diff.removed}</Chip>
                <Chip tone="review">변경 {diff.changed}</Chip>
                <Chip tone="neutral">동일 {diff.unchanged}</Chip>
              </p>
              <ol className="flex flex-col gap-3">
                {diff.entries.map((e) => {
                  const kind = KIND[e.kind];
                  const shown = e.target ?? e.base;
                  return (
                    <li
                      key={e.blockId}
                      className={[
                        "rounded-md border bg-surface-000 p-4",
                        e.kind === "UNCHANGED" ? "border-border-300" : "border-primary-600/40",
                      ].join(" ")}
                    >
                      <div className="mb-2 flex flex-wrap items-center gap-2 text-caption text-text-600">
                        <Chip tone={kind.tone}>{kind.label}</Chip>
                        <span className="font-mono">{e.blockId}</span>
                        {e.changedFields.length > 0 ? (
                          <span>· {e.changedFields.map((f) => FIELD[f] ?? f).join(", ")}</span>
                        ) : null}
                        {shown ? (
                          <span>
                            · {certaintyLabel(shown.certainty)}
                            {shown.approvedByUser ? " · 승인됨" : ""}
                          </span>
                        ) : null}
                      </div>
                      <TextDiff segments={e.textDiff} />
                      {e.kind === "CHANGED" && e.base && e.target ? (
                        <dl className="mt-2 grid grid-cols-[64px_1fr] gap-x-3 gap-y-1 text-caption text-text-600">
                          {e.base.certainty !== e.target.certainty ? (
                            <>
                              <dt>확신도</dt>
                              <dd>
                                {certaintyLabel(e.base.certainty)} →{" "}
                                {certaintyLabel(e.target.certainty)}
                              </dd>
                            </>
                          ) : null}
                          {(e.base.approvedByUser ?? false) !==
                          (e.target.approvedByUser ?? false) ? (
                            <>
                              <dt>승인</dt>
                              <dd>{e.target.approvedByUser ? "승인됨으로" : "승인 해제"}</dd>
                            </>
                          ) : null}
                          {e.changedFields.includes("references") ? (
                            <>
                              <dt>참조</dt>
                              <dd>
                                주장 {e.base.claimRefs?.length ?? 0}→
                                {e.target.claimRefs?.length ?? 0} · Evidence{" "}
                                {e.base.evidenceRefs?.length ?? 0}→
                                {e.target.evidenceRefs?.length ?? 0} · 요구사항{" "}
                                {e.base.requirementRefs?.length ?? 0}→
                                {e.target.requirementRefs?.length ?? 0}
                              </dd>
                            </>
                          ) : null}
                        </dl>
                      ) : null}
                    </li>
                  );
                })}
              </ol>
            </>
          )}
        </>
      )}
    </>
  );
}
