import type { Metadata } from "next";

import { CareerEntryList } from "@/components/career/CareerEntryList";
import { TypeFilter } from "@/components/career/TypeFilter";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { CAREER_ENTRY_TYPES } from "@/lib/labels";

export const metadata: Metadata = { title: "커리어" };

type SearchParams = Promise<{ type?: string; q?: string; cursor?: string }>;

function asType(value?: string) {
  return CAREER_ENTRY_TYPES.find((t) => t === value);
}

export default async function CareerPage({ searchParams }: { searchParams: SearchParams }) {
  const { type, q, cursor } = await searchParams;
  const typeFilter = asType(type);

  const result = await api
    .GET("/career-entries", { params: { query: { type: typeFilter, q, cursor, limit: 20 } } })
    .catch((cause: unknown) => ({ data: undefined, error: undefined, cause }));

  const addAction = (
    <ButtonLink href="/career/new">
      <Icon name="plus" size={16} />새 경력 추가
    </ButtonLink>
  );

  return (
    <>
      <PageHeader
        title="커리어"
        description="경력, 학력, 자격, 수상을 사실 단위로 관리합니다. 이 기록이 모든 지원 문서의 원천입니다."
        action={addAction}
      />
      <div className="mb-4">
        <TypeFilter current={typeFilter} q={q} />
      </div>

      {!result.data ? (
        <ErrorState
          title="커리어 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하거나 ./gradlew :api:bootRun 으로 API가 실행 중인지 확인하세요."
        />
      ) : result.data.items.length === 0 ? (
        <EmptyState
          title={typeFilter || q ? "조건에 맞는 기록이 없습니다" : "아직 등록된 경력이 없습니다"}
          description={
            typeFilter || q
              ? "다른 유형을 선택하거나 검색어를 바꿔 보세요."
              : "첫 경력을 등록하면 타임라인이 시작됩니다. 프로젝트와 성과, Evidence는 경력에 연결해 쌓아 갑니다."
          }
          action={typeFilter || q ? undefined : addAction}
        />
      ) : (
        <>
          <CareerEntryList items={result.data.items} />
          {result.data.nextCursor ? (
            <div className="mt-4 flex justify-center">
              <ButtonLink
                variant="secondary"
                href={`/career?${new URLSearchParams({
                  ...(typeFilter ? { type: typeFilter } : {}),
                  ...(q ? { q } : {}),
                  cursor: result.data.nextCursor,
                }).toString()}`}
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
