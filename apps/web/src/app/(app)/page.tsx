import type { Metadata } from "next";
import Link from "next/link";

import { AttentionList } from "@/components/dashboard/AttentionList";
import { KpiCard } from "@/components/dashboard/KpiCard";
import { VerificationChip } from "@/components/evidence/chips";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDate, formatPeriod } from "@/lib/format";
import { CAREER_ENTRY_TYPES, careerEntryTypeLabel, evidenceTypeLabel } from "@/lib/labels";

export const metadata: Metadata = { title: "커리어 대시보드" };

function chip(active: boolean) {
  return [
    "inline-flex h-8 items-center rounded-md border px-3 text-caption",
    active
      ? "border-primary-600 bg-primary-600 font-semibold text-white"
      : "border-border-300 bg-surface-000 text-text-900 hover:bg-surface-050",
  ].join(" ");
}

function delta(n: number) {
  return n > 0 ? `최근 30일 +${n}` : undefined;
}

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ timeline?: string }>;
}) {
  const { timeline } = await searchParams;
  const timelineType = CAREER_ENTRY_TYPES.find((t) => t === timeline);
  const { data } = await api
    .GET("/dashboard", { params: { query: { timelineType } } })
    .catch(() => ({ data: undefined }));

  const header = (
    <PageHeader
      title="커리어 대시보드"
      description="당신의 경험이 증명되는 커리어, ProofU와 함께하세요."
      action={
        <ButtonLink href="/career/new">
          <Icon name="plus" size={16} />새 경력 추가
        </ButtonLink>
      }
    />
  );

  if (!data) {
    return (
      <>
        {header}
        <ErrorState
          title="대시보드를 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      </>
    );
  }

  const { kpis, attention, recentEvidence } = data;
  const evidenceShare =
    kpis.verifiedEvidence.total > 0
      ? `전체의 ${Math.round((kpis.verifiedEvidence.verified * 100) / kpis.verifiedEvidence.total)}%`
      : "등록된 Evidence 없음";

  return (
    <>
      {header}
      <AttentionList items={attention} />

      <section
        aria-label="핵심 지표"
        className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4"
      >
        <KpiCard
          icon="briefcase"
          label="전체 경력 기록"
          value={kpis.careerEntries.total}
          delta={delta(kpis.careerEntries.addedLast30Days)}
          caption={`프로젝트 ${kpis.careerEntries.projects} · 성과 ${kpis.careerEntries.achievements}`}
          href="/career"
        />
        <KpiCard
          icon="file-check"
          label="검증된 Evidence"
          value={kpis.verifiedEvidence.verified}
          delta={delta(kpis.verifiedEvidence.addedLast30Days)}
          caption={
            kpis.verifiedEvidence.expired > 0
              ? `${evidenceShare} · 만료 ${kpis.verifiedEvidence.expired}`
              : evidenceShare
          }
          href="/evidence?verification=USER_VERIFIED"
        />
        <KpiCard
          icon="kanban"
          label="진행 중인 지원"
          value={kpis.activeApplications.total}
          caption={
            kpis.activeApplications.total > 0
              ? `서류 ${kpis.activeApplications.submitted} · 합격 ${kpis.activeApplications.documentPassed}`
              : "아직 지원 없음"
          }
          href="/applications"
        />
        <KpiCard
          icon="check"
          label="근거 연결률"
          value={kpis.evidenceCoverage.ratio}
          unit="%"
          caption={
            kpis.evidenceCoverage.claims > 0
              ? `주장 ${kpis.evidenceCoverage.claims}개 중 ${kpis.evidenceCoverage.supported}개에 근거 연결`
              : "등록된 주장 없음"
          }
          href="/career"
        />
      </section>

      <section className="mb-6 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-section-title">커리어 타임라인</h2>
          <div className="flex flex-wrap items-center gap-2">
            <nav aria-label="타임라인 필터" className="flex flex-wrap gap-1.5">
              <Link href="/" className={chip(!timelineType)}>
                전체
              </Link>
              {CAREER_ENTRY_TYPES.map((t) => (
                <Link key={t} href={`/?timeline=${t}`} className={chip(timelineType === t)}>
                  {careerEntryTypeLabel(t)}
                </Link>
              ))}
            </nav>
            <Link
              href="/career"
              className="text-body font-semibold text-primary-600 hover:underline"
            >
              전체보기 →
            </Link>
          </div>
        </div>
        {data.timeline.length === 0 ? (
          <EmptyState
            title={timelineType ? "해당 유형의 경력이 없습니다" : "아직 경력이 없습니다"}
            description="샘플 데이터는 만들지 않습니다. 실제 경력을 입력하면 여기에 시간순으로 표시됩니다."
          />
        ) : (
          <ol className="divide-y divide-border-300">
            {data.timeline.map((e) => (
              <li key={e.id}>
                <Link
                  href={`/career/${e.id}`}
                  className="grid grid-cols-[auto_1fr_auto] items-start gap-4 py-3 hover:bg-surface-050 md:grid-cols-[150px_1fr_auto_auto]"
                >
                  <span className="flex items-center gap-3 text-caption text-text-600 tabular-nums">
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-full bg-primary-600"
                      aria-hidden
                    />
                    {formatPeriod(e.startDate, e.endDate)}
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate text-card-title">{e.title}</span>
                    <span className="block truncate text-caption text-text-600">
                      {e.organization ?? " "}
                      {e.projectCount > 0 ? ` · 프로젝트 ${e.projectCount}` : ""}
                    </span>
                  </span>
                  <span className="hidden md:inline-flex">
                    <Chip>{careerEntryTypeLabel(e.type)}</Chip>
                  </span>
                  <span className="flex items-center gap-1 text-body text-primary-600 tabular-nums">
                    Evidence {e.evidenceCount}개
                    <Icon name="chevron-right" size={16} />
                  </span>
                </Link>
              </li>
            ))}
          </ol>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <h2 className="mb-3 text-section-title">주요 스킬</h2>
          <EmptyState
            title="스킬 관리는 준비 중입니다"
            description="경력과 프로젝트에서 스킬을 추출해 근거와 함께 보여줄 예정입니다."
          />
        </section>
        <section className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-section-title">최근 추가된 Evidence</h2>
            <Link
              href="/evidence"
              className="text-body font-semibold text-primary-600 hover:underline"
            >
              전체보기 →
            </Link>
          </div>
          {recentEvidence.length === 0 ? (
            <EmptyState
              title="아직 Evidence가 없습니다"
              description="링크, 저장소, 지표 캡처, 메모를 등록해 주장을 뒷받침하세요."
              action={
                <ButtonLink variant="secondary" href="/evidence/new">
                  Evidence 추가
                </ButtonLink>
              }
            />
          ) : (
            <ul className="divide-y divide-border-300">
              {recentEvidence.map((e) => (
                <li key={e.id}>
                  <Link
                    href={`/evidence/${e.id}`}
                    className="flex items-center gap-3 py-3 hover:bg-surface-050"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-body font-semibold">{e.title}</span>
                      <span className="block text-caption text-text-600 tabular-nums">
                        {evidenceTypeLabel(e.type)} · {formatDate(e.capturedAt)}
                      </span>
                    </span>
                    <VerificationChip value={e.verification} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
