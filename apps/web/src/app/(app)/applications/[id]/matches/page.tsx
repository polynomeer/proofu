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

export const metadata: Metadata = { title: "공고 매칭" };

function AssessmentChip({ value }: { value: Schema<"RequirementAssessment"> }) {
  const tone =
    value === "MET"
      ? "verified"
      : value === "PARTIALLY_MET"
        ? "neutral"
        : value === "UNVERIFIED"
          ? "review"
          : "expired";
  return <Chip tone={tone}>{assessmentLabel(value)}</Chip>;
}

/** J02: requirements on the left, ranked candidates with reasons on the right; gaps up top. */
export default async function MatchesPage({ params }: { params: Params }) {
  const { id } = await params;
  const [{ data: application }, { data: report }] = await Promise.all([
    api.GET("/applications/{id}", { params: { path: { id } } }),
    api.GET("/applications/{id}/matches", { params: { path: { id } } }),
  ]);
  if (!application || !report) notFound();

  const gapGroups = report.groups.filter((g) => report.gaps.includes(g.requirement.id));

  return (
    <>
      <PageHeader
        title="공고 매칭"
        description={`${application.company} · ${application.roleTitle}`}
        action={<RunMatchingButton applicationId={application.id} hasRun={report.hasRun} />}
      />
      <p className="mb-4 text-caption text-text-600">
        <Link href={`/applications/${application.id}`} className="text-primary-600 hover:underline">
          ← 지원 상세
        </Link>
        {report.lastRunAt ? (
          <span className="ml-3">마지막 실행 {formatDateTime(report.lastRunAt)}</span>
        ) : null}
        <span className="ml-3">
          점수는 정렬 기준일 뿐 합격 가능성이 아닙니다. 의미 유사도는 아직 반영되지 않아 최대
          90점입니다.
        </span>
      </p>

      {report.groups.length === 0 ? (
        <EmptyState
          title="승인된 요구사항이 없습니다"
          description="공고 상세에서 요구사항을 추가하거나 AI 초안을 승인하면 매칭할 수 있습니다."
        />
      ) : (
        <>
          {gapGroups.length > 0 ? (
            <section className="mb-6 rounded-md border border-warning-600/40 bg-warning-050 p-4">
              <h2 className="text-card-title text-warning-700">
                필수 조건 갭 {gapGroups.length}개
              </h2>
              <ul className="mt-2 flex flex-col gap-1 text-body">
                {gapGroups.map((g) => (
                  <li key={g.requirement.id} className="flex flex-wrap items-center gap-2">
                    {g.assessment ? <AssessmentChip value={g.assessment} /> : null}
                    <span>{g.requirement.text}</span>
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
            <p className="mb-6 text-body text-success-700">필수 조건에 갭이 없습니다.</p>
          ) : null}

          <ol className="flex flex-col gap-4">
            {report.groups.map((g) => (
              <li
                key={g.requirement.id}
                className="grid gap-4 rounded-md border border-border-300 bg-surface-000 p-4 lg:grid-cols-[320px_1fr]"
              >
                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap gap-2">
                    <Chip tone={g.requirement.category === "REQUIRED" ? "snapshot" : "neutral"}>
                      {requirementCategoryLabel(g.requirement.category)}
                    </Chip>
                    {g.assessment ? <AssessmentChip value={g.assessment} /> : null}
                  </div>
                  <p className="text-card-title">{g.requirement.text}</p>
                  {g.requirement.excerpt ? (
                    <p className="text-caption text-text-600">원문: “{g.requirement.excerpt}”</p>
                  ) : null}
                </div>
                <div>
                  {g.candidates.length === 0 ? (
                    <p className="text-body text-text-600">
                      {report.hasRun
                        ? "관련 주장이 없습니다."
                        : "매칭을 실행하면 관련 경력 주장과 근거가 여기에 표시됩니다."}
                    </p>
                  ) : (
                    <ul className="flex flex-col gap-2">
                      {g.candidates.map((c) => (
                        <MatchCandidateCard key={c.id} match={c} />
                      ))}
                    </ul>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </>
      )}
    </>
  );
}
