import type { Metadata } from "next";
import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "검색" };
export const dynamic = "force-dynamic";

type Results = Schema<"SearchResults">;
type Group = Schema<"SearchGroup">;
type SearchParams = Promise<{ q?: string }>;

const GROUPS: { key: keyof Omit<Results, "query">; label: string; all: string }[] = [
  { key: "careerEntries", label: "커리어", all: "/career" },
  { key: "projects", label: "프로젝트", all: "/career" },
  { key: "skills", label: "기술", all: "/career/skills" },
  { key: "evidence", label: "Evidence", all: "/evidence" },
  { key: "postings", label: "채용공고", all: "/jobs" },
];

function GroupSection({ label, group, all }: { label: string; group: Group; all: string }) {
  if (group.total === 0) return null;
  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <h2 className="flex items-baseline gap-2 text-card-title">
        {label}
        <span className="text-caption font-normal text-text-600 tabular-nums">{group.total}건</span>
      </h2>
      <ul className="mt-2 flex flex-col divide-y divide-border-300">
        {group.items.map((hit) => (
          <li key={`${hit.href}-${hit.id}`}>
            <Link href={hit.href} className="flex flex-col py-2 hover:bg-surface-050">
              <span className="text-body font-semibold">{hit.title}</span>
              {hit.subtitle ? (
                <span className="text-caption text-text-600">{hit.subtitle}</span>
              ) : null}
            </Link>
          </li>
        ))}
      </ul>
      {group.total > group.items.length ? (
        <Link
          href={all}
          className="mt-2 inline-block text-caption text-primary-600 underline-offset-2 hover:underline"
        >
          {label} 전체 보기
        </Link>
      ) : null}
    </section>
  );
}

/** The top bar's search box lands here: one query, results grouped by what was found. */
export default async function SearchPage({ searchParams }: { searchParams: SearchParams }) {
  const { q } = await searchParams;
  const query = q?.trim() ?? "";

  if (query.length < 2) {
    return (
      <>
        <PageHeader title="검색" />
        <EmptyState
          title="두 글자 이상 입력하세요"
          description="커리어, 프로젝트, 기술, Evidence, 채용공고를 한 번에 찾습니다. 회사 이름이나 기술 이름의 일부로도 검색됩니다."
        />
      </>
    );
  }

  const { data, error } = await api
    .GET("/search", { params: { query: { q: query, limit: 5 } } })
    .catch(() => ({ data: undefined, error: undefined }));

  if (!data) {
    return (
      <>
        <PageHeader title={`"${query}" 검색 결과`} />
        <ErrorState
          title="검색하지 못했습니다"
          description={error?.detail ?? "잠시 후 다시 시도하세요."}
        />
      </>
    );
  }

  const found = GROUPS.reduce((sum, g) => sum + data[g.key].total, 0);

  return (
    <>
      <PageHeader
        title={`"${data.query}" 검색 결과`}
        description={found === 0 ? undefined : `${found}건`}
      />
      {found === 0 ? (
        <EmptyState
          title="일치하는 기록이 없습니다"
          description="다른 낱말이나 더 짧은 조각으로 찾아보세요. 기술은 다른 이름(별칭)으로도 찾습니다."
        />
      ) : (
        <div className="flex flex-col gap-4">
          {GROUPS.map((g) => (
            <GroupSection key={g.key} label={g.label} group={data[g.key]} all={g.all} />
          ))}
        </div>
      )}
    </>
  );
}
