"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { ClaimStatusChip, VerificationChip } from "@/components/evidence/chips";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { Icon } from "@/components/ui/Icon";
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
        "flex flex-col gap-3 rounded-md border bg-surface-000 px-4 py-4 md:px-5",
        decision === "ACCEPTED"
          ? "border-success-600"
          : decision === "REJECTED"
            ? "border-border-300 bg-surface-050 opacity-60"
            : "border-border-300",
      ].join(" ")}
    >
      <div className="flex flex-wrap items-start gap-4">
        <span className="flex w-[72px] shrink-0 flex-col items-center rounded-md border border-border-300 py-2 tabular-nums">
          <span className="text-stat">{match.score}</span>
          <span
            className={[
              "text-caption font-semibold",
              match.band === "HIGH" ? "text-success-700" : "text-text-600",
            ].join(" ")}
          >
            {scoreBandLabel(match.band)}
          </span>
        </span>
        <div className="flex min-w-0 flex-[1_1_280px] flex-col gap-1.5">
          <p className="flex flex-wrap items-center gap-1.5">
            <ClaimStatusChip value={match.claimStatus} />
            {decision === "ACCEPTED" ? <Chip tone="verified">채택</Chip> : null}
            {decision === "REJECTED" ? <Chip tone="private">제외</Chip> : null}
          </p>
          <p className="text-card-title">{match.claimText}</p>
          {source ? (
            <Link
              href={sourceHref(source)}
              className="self-start text-caption text-primary-600 hover:underline"
            >
              {source.title ?? "원천 보기"}
            </Link>
          ) : null}
        </div>
        <div className="flex shrink-0 gap-1">
          {decision !== "ACCEPTED" ? (
            <Button
              type="button"
              variant="secondary"
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

      {match.matchedRequirementPhrase || match.matchedEvidencePhrase ? (
        <div className="grid gap-3 md:grid-cols-2">
          {match.matchedRequirementPhrase ? (
            <p className="rounded-md bg-surface-050 px-3 py-2 text-body">
              <span className="mb-1 block text-caption font-semibold text-text-600">
                요구사항에서 일치한 구절
              </span>
              “{match.matchedRequirementPhrase}”
            </p>
          ) : null}
          {match.matchedEvidencePhrase ? (
            <p className="rounded-md bg-surface-050 px-3 py-2 text-body">
              <span className="mb-1 block text-caption font-semibold text-text-600">
                근거에서 일치한 구절
              </span>
              “{match.matchedEvidencePhrase}”
            </p>
          ) : null}
        </div>
      ) : null}
      {match.reason ? (
        <p className="text-body">
          <span className="mr-1 text-caption font-semibold text-text-600">AI 설명</span>
          {match.reason}
        </p>
      ) : (
        <p className="text-caption text-text-600">설명 없음 — 점수만으로 정렬된 후보입니다.</p>
      )}
      {match.evidence.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {match.evidence.map((e) => (
            <Link
              key={e.id}
              href={`/evidence/${e.id}`}
              className="inline-flex min-h-8 items-center gap-1.5 rounded-sm border border-border-300 bg-surface-000 px-2 text-caption hover:bg-surface-050"
            >
              {e.title}
              <span className="text-text-600">· {relationLabel(e.relation)}</span>
              <VerificationChip value={e.verification} />
            </Link>
          ))}
        </div>
      ) : (
        <p className="flex items-center gap-1.5 text-caption text-warning-700">
          <Icon name="alert" size={16} />
          연결된 Evidence가 없어 근거 없음 상태입니다. 자동으로 채택되지 않습니다.
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
