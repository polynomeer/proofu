import type { Metadata } from "next";

import { ImportWizard } from "@/components/import/ImportWizard";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { importRunStatusLabel } from "@/lib/labels";
import { PAGE_LIMIT } from "@/lib/limits";

export const metadata: Metadata = { title: "자료 가져오기" };
export const dynamic = "force-dynamic";

const DATE = new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" });

/** F21 자료 가져오기 (docs/product/personal-archive-import.md). */
export default async function ImportsPage() {
  const runs = await api
    .GET("/imports", { params: { query: { limit: PAGE_LIMIT } } })
    .catch(() => ({ data: undefined }));

  return (
    <>
      <PageHeader
        back={{ href: "/career", label: "커리어" }}
        title="자료 가져오기"
        description="개인 저장소에 모아 둔 지원 이력과 공고를 번들로 들여옵니다. 무엇이 들어올지 먼저 보여 주고, 고른 것만 만듭니다. 들어온 공고 원문은 가져온 것으로 표시되고, 지원 이력은 기록된 상태 그대로 남습니다."
        action={
          <ButtonLink variant="secondary" href="/career">
            <Icon name="arrow-left" size={16} />
            커리어로
          </ButtonLink>
        }
      />

      <ImportWizard />

      <section aria-labelledby="runs-heading" className="mt-6">
        <h2 id="runs-heading" className="text-section-title">
          지난 계획
        </h2>
        {!runs.data ? (
          <div className="mt-2">
            <ErrorState
              title="지난 계획을 불러오지 못했습니다"
              description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
            />
          </div>
        ) : runs.data.items.length === 0 ? (
          <p className="mt-2 max-w-prose text-body text-text-600">
            아직 가져온 자료가 없습니다. 계획을 세우면 무엇을 살펴봤는지 여기에 남습니다.
          </p>
        ) : (
          <>
            <ul className="mt-2 divide-y divide-border-300">
              {runs.data.items.map((run) => (
                <li key={run.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                  <Chip tone={run.status === "APPLIED" ? "verified" : "neutral"}>
                    {importRunStatusLabel(run.status)}
                  </Chip>
                  <span className="text-body">{run.source ?? "출처 없음"}</span>
                  <span className="text-caption text-text-600">{run.itemCount}건</span>
                  <span className="text-caption text-text-600">
                    {DATE.format(new Date(run.createdAt))}
                  </span>
                </li>
              ))}
            </ul>
            {runs.data.nextCursor ? (
              <p className="mt-2 text-caption text-text-600">
                최근 {runs.data.items.length}건까지 보여 줍니다.
              </p>
            ) : null}
          </>
        )}
      </section>
    </>
  );
}
