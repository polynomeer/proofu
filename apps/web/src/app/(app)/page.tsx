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
import { filterChipClass } from "@/components/ui/filterChip";
import { api } from "@/lib/api";
import { formatDate, formatPeriod } from "@/lib/format";
import {
  CAREER_ENTRY_TYPES,
  careerEntryTypeLabel,
  evidenceTypeLabel,
  proficiencyLabel,
  skillCategoryLabel,
} from "@/lib/labels";

export const metadata: Metadata = { title: "커리어 대시보드" };

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
        className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4"
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
          progress={kpis.evidenceCoverage.claims > 0 ? kpis.evidenceCoverage.ratio : undefined}
          caption={
            kpis.evidenceCoverage.claims > 0
              ? `주장 ${kpis.evidenceCoverage.claims}개 중 ${kpis.evidenceCoverage.supported}개에 근거 연결`
              : "등록된 주장 없음"
          }
          href="/career"
        />
      </section>

      <div className="mb-6 grid gap-6 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <section className="min-w-0 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-section-title">커리어 타임라인</h2>
            <div className="flex flex-wrap items-center gap-2">
              <nav aria-label="타임라인 필터" className="flex flex-wrap gap-1.5">
                <Link
                  href="/"
                  aria-current={!timelineType ? "page" : undefined}
                  className={filterChipClass(!timelineType, "sm")}
                >
                  전체
                </Link>
                {CAREER_ENTRY_TYPES.map((t) => (
                  <Link
                    key={t}
                    href={`/?timeline=${t}`}
                    aria-current={timelineType === t ? "page" : undefined}
                    className={filterChipClass(timelineType === t, "sm")}
                  >
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
            <ol className="flex flex-col">
              {data.timeline.map((e, i) => {
                const last = i === data.timeline.length - 1;
                return (
                  <li
                    key={e.id}
                    className="grid grid-cols-[16px_minmax(0,1fr)] gap-x-3 md:grid-cols-[132px_16px_minmax(0,1fr)]"
                  >
                    <span className="hidden pt-0.5 text-caption text-text-600 tabular-nums md:block">
                      {formatPeriod(e.startDate, e.endDate)}
                    </span>
                    <span className="flex flex-col items-center" aria-hidden>
                      <span
                        className={[
                          "mt-1 h-3 w-3 shrink-0 rounded-full",
                          e.type === "EMPLOYMENT"
                            ? "bg-primary-600"
                            : "border-2 border-primary-600 bg-surface-000",
                        ].join(" ")}
                      />
                      {last ? null : <span className="w-0.5 flex-1 bg-border-300" />}
                    </span>
                    <div className={`flex min-w-0 flex-col gap-1.5 ${last ? "" : "pb-5"}`}>
                      <Link
                        href={`/career/${e.id}`}
                        className="text-card-title hover:text-primary-600 hover:underline"
                      >
                        {e.organization ? `${e.organization} · ` : ""}
                        {e.title}
                      </Link>
                      <span className="text-caption text-text-600 tabular-nums md:hidden">
                        {formatPeriod(e.startDate, e.endDate)}
                      </span>
                      <span className="flex flex-wrap gap-1.5">
                        <Chip>{careerEntryTypeLabel(e.type)}</Chip>
                        {e.projectCount > 0 ? <Chip>프로젝트 {e.projectCount}</Chip> : null}
                        {e.claimCount > 0 ? <Chip>주장 {e.claimCount}</Chip> : null}
                        <Chip tone={e.evidenceCount > 0 ? "verified" : "review"}>
                          Evidence {e.evidenceCount}
                        </Chip>
                      </span>
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </section>

        <section className="min-w-0 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="text-section-title">주요 기술</h2>
            {data.topSkills.total > 0 ? (
              <Link
                href="/career/skills"
                className="text-body font-semibold text-primary-600 hover:underline"
              >
                기술 관리 →
              </Link>
            ) : null}
          </div>
          {data.topSkills.total === 0 ? (
            <EmptyState
              title="아직 등록한 기술이 없습니다"
              description="자주 쓰는 언어·도구를 등록하고 프로젝트에 연결하면, 무엇이 기록으로 뒷받침되는지 여기서 한눈에 보입니다."
              action={
                <ButtonLink variant="secondary" href="/career/skills">
                  기술 등록하기
                </ButtonLink>
              }
            />
          ) : (
            <>
              <p className="mb-2 text-caption text-text-600">
                연결된 프로젝트 수 → 마지막 사용 순. 수준은 자기평가입니다.
              </p>
              <ul className="flex flex-col divide-y divide-border-300">
                {data.topSkills.items.map((skill) => (
                  <li
                    key={skill.id}
                    className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-0.5 py-2.5"
                  >
                    <span className="truncate text-body font-semibold">{skill.canonicalName}</span>
                    <Chip>
                      {skill.proficiency
                        ? `자기평가 ${proficiencyLabel(skill.proficiency)}`
                        : skillCategoryLabel(skill.category)}
                    </Chip>
                    <span className="col-span-2 text-caption text-text-600 tabular-nums">
                      {skill.projectCount > 0
                        ? `프로젝트 ${skill.projectCount}`
                        : "연결된 프로젝트 없음"}
                      {skill.lastUsedAt ? ` · 마지막 사용 ${formatDate(skill.lastUsedAt)}` : ""}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>

      <section className="rounded-md border border-border-300 bg-surface-000">
        <div className="flex items-center justify-between px-4 py-4 md:px-6">
          <h2 className="text-section-title">최근 추가된 Evidence</h2>
          <Link
            href="/evidence"
            className="text-body font-semibold text-primary-600 hover:underline"
          >
            전체보기 →
          </Link>
        </div>
        {recentEvidence.length === 0 ? (
          <div className="px-4 pb-4 md:px-6 md:pb-6">
            <EmptyState
              title="아직 Evidence가 없습니다"
              description="링크, 저장소, 지표 캡처, 메모를 등록해 주장을 뒷받침하세요."
              action={
                <ButtonLink variant="secondary" href="/evidence/new">
                  Evidence 추가
                </ButtonLink>
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse text-left text-body">
              <thead className="bg-surface-050 text-caption text-text-600">
                <tr className="border-y border-border-300">
                  <th scope="col" className="px-4 py-2.5 font-semibold md:px-6">
                    제목
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-semibold">
                    유형
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-semibold">
                    검증 상태
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-semibold md:px-6">
                    수집일
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-300">
                {recentEvidence.map((e) => (
                  <tr key={e.id} className="hover:bg-surface-050">
                    <td className="px-4 py-3 md:px-6">
                      <Link
                        href={`/evidence/${e.id}`}
                        className="font-semibold hover:text-primary-600 hover:underline"
                      >
                        {e.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-caption text-text-600">
                      {evidenceTypeLabel(e.type)}
                    </td>
                    <td className="px-4 py-3">
                      <VerificationChip value={e.verification} />
                    </td>
                    <td className="px-4 py-3 text-caption text-text-600 tabular-nums md:px-6">
                      {formatDate(e.capturedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
