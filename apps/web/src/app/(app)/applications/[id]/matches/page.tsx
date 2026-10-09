import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import type { Schema } from "@proofu/contracts";

import { MatchCandidateCard } from "@/components/matching/MatchCandidateCard";
import { RunMatchingButton } from "@/components/matching/RunMatchingButton";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { assessmentLabel, requirementCategoryLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;
type SearchParams = Promise<{ req?: string }>;
type Assessment = Schema<"RequirementAssessment">;
type Group = Schema<"RequirementMatchGroup">;

export const metadata: Metadata = { title: "공고 매칭" };

const ASSESSMENTS: readonly Assessment[] = ["MET", "PARTIALLY_MET", "UNVERIFIED", "UNMET"];

function assessmentTone(value: Assessment) {
  return value === "MET"
    ? "verified"
    : value === "PARTIALLY_MET"
      ? "neutral"
      : value === "UNVERIFIED"
        ? "review"
        : "expired";
}

function AssessmentChip({ value }: { value: Assessment }) {
  return <Chip tone={assessmentTone(value)}>{assessmentLabel(value)}</Chip>;
}

function RequirementChips({ group }: { group: Group }) {
  const accepted = group.candidates.filter((c) => c.userDecision === "ACCEPTED").length;
  return (
    <span className="flex flex-wrap items-center gap-1.5">
      <Chip tone={group.requirement.category === "REQUIRED" ? "snapshot" : "neutral"}>
        {requirementCategoryLabel(group.requirement.category)}
      </Chip>
      {group.assessment ? <AssessmentChip value={group.assessment} /> : null}
      {accepted > 0 ? <Chip tone="verified">채택 {accepted}</Chip> : null}
    </span>
  );
}

/**
 * J02: requirements on the left, the selected requirement's ranked candidates on the right
 * (`?req=`), gaps under them. Without a selection the first requirement that has candidates
 * opens, so the page always starts on something the user can decide.
 */
export default async function MatchesPage({
  params,
  searchParams,
}: {
  params: Params;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const { req } = await searchParams;
  const [{ data: application }, { data: report }] = await Promise.all([
    api.GET("/applications/{id}", { params: { path: { id } } }),
    api.GET("/applications/{id}/matches", { params: { path: { id } } }),
  ]);
  if (!application || !report) notFound();

  const groups = report.groups;
  const selected =
    groups.find((g) => g.requirement.id === req) ??
    groups.find((g) => g.candidates.length > 0) ??
    groups[0];
  const gapGroups = groups.filter((g) => report.gaps.includes(g.requirement.id));
  const required = groups.filter((g) => g.requirement.category === "REQUIRED");
  const acceptedTotal = groups.reduce(
    (n, g) => n + g.candidates.filter((c) => c.userDecision === "ACCEPTED").length,
    0,
  );

  return (
    <>
      <PageHeader
        title="공고 매칭"
        description={`${application.company} · ${application.roleTitle}`}
        action={<RunMatchingButton applicationId={application.id} hasRun={report.hasRun} />}
      />

      <section
        aria-label="매칭 요약"
        className="mb-6 flex flex-col gap-3 rounded-md border border-border-300 bg-surface-000 p-4 md:px-5"
      >
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-caption text-text-600">
          <Link
            href={`/applications/${application.id}`}
            className="text-primary-600 hover:underline"
          >
            ← 지원 상세
          </Link>
          <Chip tone="snapshot">공고 스냅샷 {formatDateTime(application.snapshot.capturedAt)}</Chip>
          <span>
            요구사항 {groups.length}개 (필수 {required.length}) · 채택 {acceptedTotal}건
          </span>
          {report.lastRunAt ? <span>마지막 실행 {formatDateTime(report.lastRunAt)}</span> : null}
        </p>
        {required.length > 0 && report.hasRun ? (
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border-300 pt-3 text-body">
            <span className="font-semibold">필수 요구사항</span>
            {ASSESSMENTS.map((a) => {
              const n = required.filter((g) => g.assessment === a).length;
              return n > 0 ? (
                <span key={a} className="flex items-center gap-1.5 tabular-nums">
                  <AssessmentChip value={a} />
                  {n}
                </span>
              ) : null;
            })}
            <span className="text-caption text-text-600 lg:ml-auto">
              점수는 정렬 기준일 뿐 합격 가능성이 아닙니다. 의미 유사도는 아직 반영되지 않아 최대
              90점입니다.
            </span>
          </div>
        ) : (
          <p className="text-caption text-text-600">
            점수는 정렬 기준일 뿐 합격 가능성이 아닙니다. 의미 유사도는 아직 반영되지 않아 최대
            90점입니다.
          </p>
        )}
      </section>

      {groups.length === 0 || !selected ? (
        <EmptyState
          title="승인된 요구사항이 없습니다"
          description="공고 상세에서 요구사항을 추가하거나 AI 초안을 승인하면 매칭할 수 있습니다."
        />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
          <nav aria-labelledby="requirements-title" className="flex flex-col gap-2">
            <h2 id="requirements-title" className="mb-1 text-section-title">
              요구사항
            </h2>
            <ol className="flex flex-col gap-2">
              {groups.map((g) => {
                const active = g.requirement.id === selected.requirement.id;
                return (
                  <li key={g.requirement.id}>
                    <Link
                      href={`/applications/${application.id}/matches?req=${g.requirement.id}`}
                      scroll={false}
                      aria-current={active ? "true" : undefined}
                      className={[
                        "flex flex-col gap-1.5 rounded-md border bg-surface-000 px-3.5 py-3 hover:bg-surface-050",
                        active
                          ? "border-primary-600 bg-primary-050 shadow-[inset_0_0_0_1px_var(--color-primary-600)] hover:bg-primary-050"
                          : "border-border-300",
                      ].join(" ")}
                    >
                      <RequirementChips group={g} />
                      <span className="text-body font-semibold">{g.requirement.text}</span>
                      <span className="text-caption text-text-600 tabular-nums">
                        후보 {g.candidates.length}건
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ol>
          </nav>

          <div className="flex min-w-0 flex-col gap-4">
            <section aria-labelledby="candidates-title" className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <h2 id="candidates-title" className="text-section-title">
                  추천 근거
                </h2>
                <p className="text-body">{selected.requirement.text}</p>
                {selected.requirement.excerpt ? (
                  <p className="rounded-md bg-surface-050 px-3 py-2 text-caption text-text-600">
                    원문: “{selected.requirement.excerpt}”
                  </p>
                ) : null}
              </div>
              {selected.candidates.length === 0 ? (
                <p className="rounded-md border border-border-300 bg-surface-000 p-4 text-body text-text-600">
                  {report.hasRun
                    ? "관련 주장이 없습니다. 경력을 보완하거나 주장을 직접 추가하세요."
                    : "매칭을 실행하면 관련 경력 주장과 근거가 여기에 표시됩니다."}
                </p>
              ) : (
                <ul className="flex flex-col gap-3">
                  {selected.candidates.map((c) => (
                    <MatchCandidateCard key={c.id} match={c} />
                  ))}
                </ul>
              )}
            </section>

            {gapGroups.length > 0 ? (
              <section
                aria-labelledby="gaps-title"
                className="rounded-md border border-border-300 bg-surface-050 p-4 md:px-5"
              >
                <h2 id="gaps-title" className="text-card-title">
                  필수 조건 갭 {gapGroups.length}개
                </h2>
                <ul className="mt-2 flex flex-col gap-2 text-body">
                  {gapGroups.map((g) => (
                    <li key={g.requirement.id} className="flex flex-wrap items-center gap-2">
                      {g.assessment ? <AssessmentChip value={g.assessment} /> : null}
                      <Link
                        href={`/applications/${application.id}/matches?req=${g.requirement.id}`}
                        scroll={false}
                        className="font-semibold hover:text-primary-600 hover:underline"
                      >
                        {g.requirement.text}
                      </Link>
                      <span className="text-caption text-text-600">
                        {g.assessment === "UNVERIFIED"
                          ? "— 관련 주장은 있지만 Evidence가 연결되지 않았습니다"
                          : "— 관련 경력·주장이 없습니다. 경력을 보완하거나 직접 입력하세요"}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ) : report.hasRun ? (
              <p className="text-body text-success-700">필수 조건에 갭이 없습니다.</p>
            ) : null}
          </div>
        </div>
      )}
    </>
  );
}
