import type { Metadata } from "next";

import { JobPostingCard } from "@/components/jobs/JobPostingCard";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "채용공고" };

export default async function JobsPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; cursor?: string }>;
}) {
  const { q, cursor } = await searchParams;
  const result = await api
    .GET("/job-postings", { params: { query: { q, cursor, limit: 20 } } })
    .catch(() => ({ data: undefined }));

  const addAction = (
    <ButtonLink href="/jobs/new">
      <Icon name="plus" size={16} />
      공고 저장
    </ButtonLink>
  );

  return (
    <>
      <PageHeader
        title="채용공고"
        description="지원 대상 공고를 저장하고 원문을 불변 스냅샷으로 보존합니다. 요구사항 분석과 매칭은 저장된 스냅샷을 기준으로 합니다."
        action={addAction}
      />
      <form action="/jobs" className="mb-4 flex gap-2">
        <label htmlFor="q" className="sr-only">
          공고 검색
        </label>
        <input
          id="q"
          name="q"
          type="search"
          defaultValue={q}
          placeholder="회사, 직무, 본문 키워드"
          className="h-10 w-full max-w-md rounded-md border border-border-300 bg-surface-000 px-3 text-body"
        />
        <button
          type="submit"
          className="h-10 rounded-md border border-border-300 bg-surface-000 px-4 text-body font-semibold hover:bg-surface-050"
        >
          검색
        </button>
      </form>

      {!result.data ? (
        <ErrorState
          title="공고 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : result.data.items.length === 0 ? (
        <EmptyState
          title={q ? "검색 결과가 없습니다" : "아직 저장된 공고가 없습니다"}
          description={
            q
              ? "다른 키워드로 검색해 보세요."
              : "관심 있는 채용 페이지의 본문을 붙여넣어 저장하세요. 이후 요구사항 추출과 경력 매칭이 이 원문을 기준으로 진행됩니다."
          }
          action={q ? undefined : addAction}
        />
      ) : (
        <>
          <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {result.data.items.map((p) => (
              <JobPostingCard key={p.id} posting={p} />
            ))}
          </ul>
          {result.data.nextCursor ? (
            <div className="mt-4 flex justify-center">
              <ButtonLink
                variant="secondary"
                href={`/jobs?${new URLSearchParams({ ...(q ? { q } : {}), cursor: result.data.nextCursor })}`}
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
