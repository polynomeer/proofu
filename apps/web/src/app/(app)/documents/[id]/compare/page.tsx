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
type SearchParams = Promise<{ base?: string; target?: string; hide?: string }>;
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
  ADDED: { label: "+ 추가", tone: "verified" },
  REMOVED: { label: "− 삭제", tone: "expired" },
  CHANGED: { label: "~ 변경", tone: "review" },
  UNCHANGED: { label: "= 동일", tone: "neutral" },
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
          <ins
            key={i}
            className="rounded-sm bg-success-050 px-0.5 text-text-900 underline decoration-success-600 decoration-2 underline-offset-[3px]"
          >
            {s.text}
          </ins>
        ) : (
          <del key={i} className="rounded-sm bg-neutral-100 px-0.5 text-neutral-700 decoration-2">
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
  const { base, target, hide } = await searchParams;
  const hideUnchanged = hide === "1";
  const [{ data: doc }, { data: versions }] = await Promise.all([
    api.GET("/documents/{id}", { params: { path: { id } } }),
    api.GET("/documents/{id}/versions", { params: { path: { id } } }),
  ]);
  if (!doc || !versions) notFound();
  const { data: submissions } = await api.GET("/applications/{id}/submissions", {
    params: { path: { id: doc.applicationId } },
  });
  const submittedAt = new Map(
    (submissions?.items ?? [])
      .filter((s) => s.documentVersionId)
      .map((s) => [s.documentVersionId, s.submittedAt] as const),
  );
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
      <PageHeader
        back={{ href: `/documents/${doc.id}`, label: "문서 편집" }}
        title="버전 비교"
        description={doc.title}
      />
      <p className="mb-4 text-caption text-text-600">
        <span>
          블록 id 기준으로 추가·삭제·변경을 표시합니다. 문장 안의 단어 단위 표시는 삭제(취소선)와
          추가(강조)입니다.
        </span>
      </p>

      {items.length < 2 ? (
        <p className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6 text-body text-text-600">
          비교하려면 버전이 두 개 이상 필요합니다.
        </p>
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,300px)_minmax(0,1fr)]">
          <section
            aria-labelledby="versions-title"
            className="overflow-hidden rounded-md border border-border-300 bg-surface-000"
          >
            <h2 id="versions-title" className="px-4 pt-4 pb-2 text-card-title">
              버전 {items.length}개
            </h2>
            <p className="px-4 pb-3 text-caption text-text-600">
              버전은 추가만 되며 고쳐 쓰지 않습니다.
            </p>
            <ol className="border-t border-border-300">
              {items.map((v) => {
                const role = v.id === targetId ? "B" : v.id === baseId ? "A" : null;
                const submitted = submittedAt.get(v.id);
                return (
                  <li
                    key={v.id}
                    className={[
                      "flex flex-col gap-1 border-b border-border-300 px-4 py-3 last:border-b-0",
                      role === "B"
                        ? "bg-primary-050 shadow-[inset_3px_0_0_var(--color-primary-600)]"
                        : role === "A"
                          ? "bg-surface-050 shadow-[inset_3px_0_0_var(--color-text-600)]"
                          : "",
                    ].join(" ")}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-semibold">
                        {v.label ?? versionAuthorLabel(v.createdBy)}
                      </span>
                      {role ? (
                        <Chip tone={role === "B" ? "snapshot" : "neutral"}>
                          {role === "B" ? "대상 B" : "기준 A"}
                        </Chip>
                      ) : null}
                    </span>
                    <span className="text-caption text-text-600 tabular-nums">
                      {versionAuthorLabel(v.createdBy)} · {formatDateTime(v.createdAt)} · 블록{" "}
                      {v.blocks.length}
                    </span>
                    {submitted ? (
                      <span className="self-start">
                        <Chip tone="snapshot">제출 스냅샷 · {formatDateTime(submitted)}</Chip>
                      </span>
                    ) : null}
                    {role === null && targetId ? (
                      <Link
                        href={`/documents/${doc.id}/compare?base=${v.id}&target=${targetId}`}
                        className="self-start text-caption text-primary-600 hover:underline"
                      >
                        기준(A)으로 비교
                      </Link>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          </section>

          <section
            aria-labelledby="diff-title"
            className="min-w-0 overflow-hidden rounded-md border border-border-300 bg-surface-000"
          >
            <form
              method="get"
              className="flex flex-wrap items-end gap-3 border-b border-border-300 px-4 py-4 md:px-5"
            >
              <h2 id="diff-title" className="w-full text-section-title sm:w-auto sm:self-center">
                비교
              </h2>
              <label className="flex min-w-0 flex-[1_1_200px] flex-col gap-1 text-caption">
                <span className="font-semibold">기준 A (이전)</span>
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
              <label className="flex min-w-0 flex-[1_1_200px] flex-col gap-1 text-caption">
                <span className="font-semibold">대상 B (이후)</span>
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
              <label className="flex h-10 items-center gap-2 text-body">
                <input type="checkbox" name="hide" value="1" defaultChecked={hideUnchanged} />
                동일한 블록 숨기기
              </label>
              <button
                type="submit"
                className="inline-flex h-10 items-center justify-center rounded-md bg-primary-600 px-4 text-body font-semibold text-white hover:bg-primary-700"
              >
                비교
              </button>
            </form>

            {!diff ? (
              <p className="p-5 text-body text-text-600">서로 다른 두 버전을 고르세요.</p>
            ) : (
              <>
                <p className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-border-300 bg-surface-050 px-4 py-3 text-caption md:px-5">
                  <span className="flex items-center gap-1.5 tabular-nums">
                    <Chip tone="verified">추가 {diff.added}</Chip>
                  </span>
                  <span className="flex items-center gap-1.5 tabular-nums">
                    <Chip tone="expired">삭제 {diff.removed}</Chip>
                  </span>
                  <span className="flex items-center gap-1.5 tabular-nums">
                    <Chip tone="review">변경 {diff.changed}</Chip>
                  </span>
                  <span className="flex items-center gap-1.5 tabular-nums">
                    <Chip tone="neutral">동일 {diff.unchanged}</Chip>
                  </span>
                  <span className="text-text-600 sm:ml-auto">
                    블록 id 기준 · 단어 단위 (추가는 밑줄, 삭제는 취소선)
                  </span>
                </p>
                <ol>
                  {diff.entries
                    .filter((e) => !(hideUnchanged && e.kind === "UNCHANGED"))
                    .map((e) => {
                      const kind = KIND[e.kind];
                      const shown = e.target ?? e.base;
                      return (
                        <li
                          key={e.blockId}
                          className={[
                            "grid gap-3 border-b border-border-300 px-4 py-4 last:border-b-0 sm:grid-cols-[112px_minmax(0,1fr)] md:px-5",
                            e.kind === "ADDED" ? "bg-surface-050" : "",
                          ].join(" ")}
                        >
                          <div className="flex flex-row flex-wrap items-center gap-2 sm:flex-col sm:items-start sm:gap-1">
                            <Chip tone={kind.tone}>{kind.label}</Chip>
                            <span className="font-mono text-caption text-text-600">
                              {e.blockId}
                            </span>
                          </div>
                          <div className="min-w-0">
                            <div className={e.kind === "UNCHANGED" ? "text-text-600" : ""}>
                              <TextDiff segments={e.textDiff} />
                            </div>
                            <p className="mt-1 text-caption text-text-600">
                              {e.changedFields.length > 0
                                ? `${e.changedFields.map((f) => FIELD[f] ?? f).join(", ")} 바뀜`
                                : null}
                              {e.changedFields.length > 0 && shown ? " · " : null}
                              {shown
                                ? `${certaintyLabel(shown.certainty)}${shown.approvedByUser ? " · 승인됨" : ""}`
                                : null}
                            </p>
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
                          </div>
                        </li>
                      );
                    })}
                </ol>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
