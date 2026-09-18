"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { ClaimStatusChip, VerificationChip } from "@/components/evidence/chips";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { api } from "@/lib/api";
import { relationLabel, scoreBandLabel } from "@/lib/labels";

type Match = Schema<"RequirementMatch">;

function sourceHref(source: Schema<"ClaimSource">): string {
  switch (source.type) {
    case "CAREER_ENTRY":
      return `/career/${source.id}`;
    case "PROJECT":
      return `/projects/${source.id}`;
    case "ACHIEVEMENT":
      return source.projectId ? `/projects/${source.projectId}` : "/career";
    default:
      return "/career";
  }
}

/** One recommendation: number + band label (never a probability), reason, evidence, and the user's call. */
export function MatchCandidateCard({ match }: { match: Match }) {
  const router = useRouter();
  const [decision, setDecision] = useState(match.userDecision ?? null);
  const [busy, setBusy] = useState(false);

  async function decide(next: "ACCEPTED" | "REJECTED" | null) {
    setBusy(true);
    const { data, error } = await api.PUT("/requirement-matches/{id}/decision", {
      params: { path: { id: match.id } },
      body: { decision: next },
    });
    setBusy(false);
    if (error || !data) {
      window.alert(error?.detail ?? "결정을 저장하지 못했습니다.");
      return;
    }
    setDecision(data.userDecision ?? null);
    router.refresh();
  }

  const source = match.sources[0];
  return (
    <li
      className={[
        "flex flex-col gap-2 rounded-md border p-3",
        decision === "ACCEPTED"
          ? "border-success-600 bg-success-050/40"
          : decision === "REJECTED"
            ? "border-border-300 bg-surface-050 opacity-60"
            : "border-border-300 bg-surface-000",
      ].join(" ")}
    >
      <div className="flex items-start gap-3">
        <span className="flex w-14 shrink-0 flex-col items-center rounded-md bg-primary-050 py-1 tabular-nums">
          <span className="text-section-title text-primary-700">{match.score}</span>
          <span className="text-caption text-text-600">{scoreBandLabel(match.band)}</span>
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-body font-semibold">{match.claimText}</p>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-caption text-text-600">
            <ClaimStatusChip value={match.claimStatus} />
            {source ? (
              <Link href={sourceHref(source)} className="text-primary-600 hover:underline">
                {source.title ?? "원천 보기"}
              </Link>
            ) : null}
            {decision === "ACCEPTED" ? <Chip tone="verified">채택</Chip> : null}
            {decision === "REJECTED" ? <Chip tone="private">제외</Chip> : null}
          </p>
        </div>
        <div className="flex shrink-0 gap-1">
          {decision !== "ACCEPTED" ? (
            <Button
              type="button"
              variant="tertiary"
              onClick={() => decide("ACCEPTED")}
              loading={busy}
            >
              채택
            </Button>
          ) : null}
          {decision !== "REJECTED" ? (
            <Button
              type="button"
              variant="tertiary"
              onClick={() => decide("REJECTED")}
              disabled={busy}
            >
              제외
            </Button>
          ) : null}
          {decision !== null ? (
            <Button type="button" variant="tertiary" onClick={() => decide(null)} disabled={busy}>
              되돌리기
            </Button>
          ) : null}
        </div>
      </div>

      {match.reason ? (
        <p className="rounded-md bg-surface-050 px-3 py-2 text-body">
          <span className="mr-1 text-caption text-text-600">AI 설명</span>
          {match.reason}
        </p>
      ) : (
        <p className="text-caption text-text-600">설명 없음 — 점수만으로 정렬된 후보입니다.</p>
      )}
      {match.matchedRequirementPhrase || match.matchedEvidencePhrase ? (
        <p className="text-caption text-text-600">
          {match.matchedRequirementPhrase ? <>요구: “{match.matchedRequirementPhrase}” </> : null}
          {match.matchedEvidencePhrase ? <>↔ 근거: “{match.matchedEvidencePhrase}”</> : null}
        </p>
      ) : null}
      {match.evidence.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {match.evidence.map((e) => (
            <Link
              key={e.id}
              href={`/evidence/${e.id}`}
              className="inline-flex h-6 items-center gap-1 rounded-sm border border-border-300 bg-surface-050 px-2 text-caption hover:bg-surface-100"
            >
              {e.title}
              <span className="text-text-600">· {relationLabel(e.relation)}</span>
              <VerificationChip value={e.verification} />
            </Link>
          ))}
        </div>
      ) : (
        <p className="text-caption text-warning-700">
          연결된 Evidence가 없어 근거 없음 상태입니다.
        </p>
      )}
      <details className="text-caption text-text-600">
        <summary className="cursor-pointer">점수 구성</summary>
        <span className="tabular-nums">
          커버리지 {pct(match.features.requirementCoverage)} · Evidence{" "}
          {pct(match.features.evidenceStrength)} · 최근성 {pct(match.features.recency)} · 복잡도{" "}
          {pct(match.features.complexity)} · 영향 {pct(match.features.impact)} · 의미 유사도{" "}
          {pct(match.features.semanticSimilarity)} (미사용)
        </span>
      </details>
    </li>
  );
}

function pct(v: number) {
  return `${Math.round(v * 100)}`;
}
