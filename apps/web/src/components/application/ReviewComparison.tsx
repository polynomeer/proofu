"use client";

import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { atsTone } from "@/components/document/AtsPanel";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { formatDateTime } from "@/lib/format";
import {
  assessmentLabel,
  atsSeverityLabel,
  documentTypeLabel,
  requirementCategoryLabel,
  versionAuthorLabel,
} from "@/lib/labels";

type Context = Schema<"ReviewContext">;
type Requirement = Context["requirements"][number];

function assessmentTone(a: Requirement["assessment"]) {
  return a === "MET"
    ? "verified"
    : a === "PARTIALLY_MET"
      ? "neutral"
      : a === "UNVERIFIED"
        ? "review"
        : "expired";
}

/** One observable line per requirement; wording describes the record, never a cause. */
export function requirementFactLine(r: Requirement, hasSubmission: boolean): string {
  const parts = [
    r.assessment ? `매칭 판정 ${assessmentLabel(r.assessment)}` : null,
    `채택 후보 ${r.acceptedCandidates ?? 0}건`,
    hasSubmission
      ? r.addressedBySubmission
        ? "제출 문서에서 다룸"
        : "제출 문서에서 다루지 않음"
      : null,
  ].filter(Boolean);
  return `${requirementCategoryLabel(r.category)} 요구사항 “${r.text}”: ${parts.join(", ")}`;
}

function QuoteButton({ onQuote, line }: { onQuote: (line: string) => void; line: string }) {
  return (
    <Button
      type="button"
      variant="tertiary"
      className="h-7 px-2 text-caption"
      onClick={() => onQuote(line)}
      title="회고의 근거 필드에 이 줄을 추가합니다"
    >
      근거로 인용
    </Button>
  );
}

/**
 * A02 "비교 대상": posting snapshot, submitted version, document facts and match verdicts.
 * Every line is something the user can quote into a review's rationale.
 */
export function ReviewComparison({
  context,
  onQuote,
}: {
  context: Context;
  onQuote: (line: string) => void;
}) {
  const s = context.submission;
  const d = context.document;
  const hasSubmission = Boolean(s);
  const unaddressedRequired = context.requirements.filter(
    (r) => r.category === "REQUIRED" && hasSubmission && !r.addressedBySubmission,
  );

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border-300 bg-surface-050 p-4">
      <h3 className="text-card-title">비교 대상</h3>
      <p className="text-caption text-text-600">
        기록된 사실만 보여줍니다. 탈락 원인은 알 수 없으므로 아래 줄을 인용할 때도 가설로
        표현하세요.
      </p>

      <dl className="grid gap-x-3 gap-y-2 text-body sm:grid-cols-[96px_minmax(0,1fr)]">
        <dt className="text-text-600">공고 스냅샷</dt>
        <dd className="flex flex-wrap items-center gap-2">
          <Link
            href={`/jobs/${context.postingSnapshot.postingId}?snapshot=${context.postingSnapshot.id}`}
            className="text-primary-600 hover:underline"
          >
            {formatDateTime(context.postingSnapshot.capturedAt)} 저장본
          </Link>
          <span className="text-caption text-text-600">
            승인된 요구사항 {context.postingSnapshot.approvedRequirementCount}개
            {context.matchRunAt
              ? ` · 매칭 ${formatDateTime(context.matchRunAt)}`
              : " · 매칭 미실행"}
          </span>
        </dd>

        <dt className="text-text-600">제출 문서</dt>
        <dd className="flex flex-wrap items-center gap-2">
          {s ? (
            <>
              <Chip tone="snapshot">제출 스냅샷</Chip>
              <Link href={`/submissions/${s.id}`} className="text-primary-600 hover:underline">
                {documentTypeLabel(s.documentType)} · {s.documentTitle} —{" "}
                {s.versionLabel ?? versionAuthorLabel(s.versionCreatedBy)}
              </Link>
              <span className="text-caption text-text-600">
                제출 {formatDateTime(s.submittedAt)}
                {context.submissionCount > 1
                  ? ` · 스냅샷 ${context.submissionCount}개 중 최신`
                  : ""}
              </span>
            </>
          ) : (
            <span className="text-text-600">
              제출 스냅샷이 없습니다. 제출 당시 문서를 비교할 수 없습니다.
            </span>
          )}
        </dd>

        {d ? (
          <>
            <dt className="text-text-600">문서 사실</dt>
            <dd className="flex flex-col gap-1">
              <span className="flex flex-wrap items-center gap-2">
                <span>
                  블록 {d.totalBlocks}개 · 주장 인용 {d.blocksWithClaims}개 (
                  <span className="tabular-nums">{d.evidenceLinkRate}%</span>) · 근거 있음{" "}
                  {d.supportedBlocks}개
                </span>
                <QuoteButton
                  onQuote={onQuote}
                  line={`제출 문서 ${d.totalBlocks}개 블록 중 ${d.blocksWithClaims}개가 주장을 인용 (Evidence 연결률 ${d.evidenceLinkRate}%), 근거 있음 ${d.supportedBlocks}개`}
                />
              </span>
              <span className="flex flex-wrap items-center gap-2">
                <span>
                  근거 없이 승인한 블록 {d.approvedWithoutEvidence}개 · 요구사항 커버리지{" "}
                  <span className="tabular-nums">{d.requirementCoverage}%</span>
                </span>
                <QuoteButton
                  onQuote={onQuote}
                  line={`근거 없이 승인한 블록 ${d.approvedWithoutEvidence}개, 승인된 요구사항 커버리지 ${d.requirementCoverage}%`}
                />
              </span>
            </dd>
          </>
        ) : null}

        <dt className="text-text-600">ATS 검사</dt>
        <dd>
          {context.ats ? (
            <ul className="flex flex-col gap-1">
              {context.ats.findings
                .filter((f) => f.severity !== "PASS")
                .map((f) => (
                  <li key={f.code} className="flex flex-wrap items-center gap-2">
                    <Chip tone={atsTone(f.severity)}>{atsSeverityLabel(f.severity)}</Chip>
                    <span>{f.message}</span>
                    {f.details.length > 0 ? (
                      <span className="text-caption text-text-600">{f.details.join(" · ")}</span>
                    ) : null}
                    <QuoteButton
                      onQuote={onQuote}
                      line={`ATS 검사 ${f.code}: ${f.message}${f.details.length > 0 ? ` (${f.details.join(", ")})` : ""}`}
                    />
                  </li>
                ))}
              {context.ats.findings.every((f) => f.severity === "PASS") ? (
                <li className="text-text-600">모든 항목 통과</li>
              ) : null}
            </ul>
          ) : (
            <span className="text-text-600">제출 스냅샷이 없어 검사할 문서가 없습니다.</span>
          )}
        </dd>
      </dl>

      {context.requirements.length > 0 ? (
        <div>
          <h4 className="mb-1 text-body font-semibold">
            요구사항별 기록
            {unaddressedRequired.length > 0 ? (
              <span className="ml-2 text-caption font-normal text-warning-700">
                제출 문서가 다루지 않은 필수 항목 {unaddressedRequired.length}개
              </span>
            ) : null}
          </h4>
          <ul className="flex flex-col divide-y divide-border-300">
            {context.requirements.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-2 py-2 text-body">
                <Chip tone={r.category === "REQUIRED" ? "snapshot" : "neutral"}>
                  {requirementCategoryLabel(r.category)}
                </Chip>
                <span className="min-w-0 flex-1">{r.text}</span>
                {r.assessment ? (
                  <Chip tone={assessmentTone(r.assessment)}>{assessmentLabel(r.assessment)}</Chip>
                ) : null}
                <span className="text-caption text-text-600 tabular-nums">
                  채택 {r.acceptedCandidates ?? 0}
                  {hasSubmission
                    ? r.addressedBySubmission
                      ? " · 문서에서 다룸"
                      : " · 문서에 없음"
                    : ""}
                </span>
                <QuoteButton onQuote={onQuote} line={requirementFactLine(r, hasSubmission)} />
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
