import type { Metadata } from "next";

import { CareerLogSection } from "@/components/careerlog/CareerLogSection";
import { PromotedFilter } from "@/components/careerlog/PromotedFilter";
import { ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { PAGE_LIMIT, PICKER_LIMIT } from "@/lib/limits";

export const metadata: Metadata = { title: "커리어 로그" };
export const dynamic = "force-dynamic";

type SearchParams = Promise<{ promoted?: string; cursor?: string }>;

function asPromoted(value?: string): boolean | undefined {
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}

/** F20 커리어 로그 (docs/ux/information-architecture.md: 커리어 → 로그). */
export default async function CareerLogsPage({ searchParams }: { searchParams: SearchParams }) {
  const { promoted: promotedParam, cursor } = await searchParams;
  const promoted = asPromoted(promotedParam);

  const [logs, entries, projects] = await Promise.all([
    api
      .GET("/career-logs", { params: { query: { promoted, cursor, limit: PAGE_LIMIT } } })
      .catch(() => ({ data: undefined })),
    api
      .GET("/career-entries", { params: { query: { limit: PICKER_LIMIT } } })
      .catch(() => ({ data: undefined })),
    api
      .GET("/projects", { params: { query: { limit: PICKER_LIMIT } } })
      .catch(() => ({ data: undefined })),
  ]);

  const entryItems = entries.data?.items ?? [];
  const projectItems = projects.data?.items ?? [];

  return (
    <>
      <PageHeader
        back={{ href: "/career", label: "커리어" }}
        title="커리어 로그"
        description="그때그때 적어 두는 비공개 업무 기록입니다. 공유되지 않고 내보내기에도 들어가지 않으며, 성과로 승격하면 보통의 성과가 됩니다."
        action={
          <ButtonLink variant="secondary" href="/career">
            <Icon name="arrow-left" size={16} />
            커리어로
          </ButtonLink>
        }
      />
      <div className="mb-4">
        <PromotedFilter current={promoted} />
      </div>
      {!logs.data ? (
        <ErrorState
          title="로그를 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : (
        <CareerLogSection
          initialItems={logs.data.items}
          initialCursor={logs.data.nextCursor ?? null}
          promoted={promoted}
          entries={entryItems}
          projects={projectItems}
          pickersTruncated={Boolean(entries.data?.nextCursor) || Boolean(projects.data?.nextCursor)}
        />
      )}
    </>
  );
}
