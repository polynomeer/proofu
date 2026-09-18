import type { Metadata } from "next";
import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { ApplicationStatusChip } from "@/components/application/chips";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDaysAgo, formatDday } from "@/lib/format";
import { APPLICATION_BOARD_COLUMNS } from "@/lib/labels";

export const metadata: Metadata = { title: "지원 관리" };

/** Always read live data; a prerendered board would freeze the build-time API state. */
export const dynamic = "force-dynamic";

type Application = Schema<"Application">;

function Card({ a, showStatus }: { a: Application; showStatus: boolean }) {
  return (
    <li>
      <Link
        href={`/applications/${a.id}`}
        className="flex flex-col gap-2 rounded-md border border-border-300 bg-surface-000 p-3 hover:bg-surface-050"
      >
        <span className="text-card-title leading-snug">{a.roleTitle}</span>
        <span className="text-body text-text-600">{a.company}</span>
        <span className="flex flex-wrap items-center gap-2 text-caption text-text-600 tabular-nums">
          {showStatus ? <ApplicationStatusChip value={a.status} /> : null}
          {a.status === "DOCUMENT_REJECTED" || a.status === "NO_RESPONSE" ? (
            <span className="font-semibold text-warning-700">회고 필요</span>
          ) : null}
          {a.deadlineAt ? (
            <span className="font-semibold text-text-900">{formatDday(a.deadlineAt)}</span>
          ) : null}
          <span>{formatDaysAgo(a.statusChangedAt)} 변경</span>
        </span>
      </Link>
    </li>
  );
}

/** A01 board: one column per lifecycle stage, cards ordered by last status change. */
export default async function ApplicationsPage() {
  const { data } = await api.GET("/applications", {}).catch(() => ({ data: undefined }));
  const addAction = (
    <ButtonLink href="/jobs">
      <Icon name="plus" size={16} />
      공고에서 지원 만들기
    </ButtonLink>
  );

  return (
    <>
      <PageHeader
        title="지원 관리"
        description="지원 건마다 상태 변경을 이벤트로 기록합니다. 상태는 되돌리지 않고 다음 단계로만 옮깁니다."
        action={addAction}
      />
      {!data ? (
        <ErrorState
          title="지원 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : data.items.length === 0 ? (
        <EmptyState
          title="아직 지원 건이 없습니다"
          description="저장한 채용공고의 상세 화면에서 지원을 만들면 관심 단계부터 관리할 수 있습니다."
          action={addAction}
        />
      ) : (
        <div className="-mx-4 overflow-x-auto px-4 pb-2 md:-mx-6 md:px-6">
          <div className="grid min-w-[1080px] grid-cols-6 gap-3">
            {APPLICATION_BOARD_COLUMNS.map((col) => {
              const items = data.items.filter((a) => col.statuses.includes(a.status));
              return (
                <section
                  key={col.key}
                  aria-label={col.label}
                  className="flex flex-col gap-2 rounded-md bg-surface-050 p-2"
                >
                  <h2 className="flex items-center justify-between px-1 text-caption font-semibold text-text-600">
                    {col.label}
                    <span className="tabular-nums">{items.length}</span>
                  </h2>
                  {items.length === 0 ? (
                    <p className="px-1 py-2 text-caption text-text-600">없음</p>
                  ) : (
                    <ul className="flex flex-col gap-2">
                      {items.map((a) => (
                        <Card key={a.id} a={a} showStatus={col.statuses.length > 1} />
                      ))}
                    </ul>
                  )}
                </section>
              );
            })}
          </div>
        </div>
      )}
    </>
  );
}
