"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

type Phase =
  | { kind: "idle" }
  | { kind: "queued"; jobId: string; since: number }
  | { kind: "done"; extracted: number; withoutSpan: number }
  | { kind: "failed"; message: string };

const POLL_MS = 1500;
const TIMEOUT_MS = 120_000;

const ERROR_MESSAGES: Record<string, string> = {
  AI_BUDGET_EXCEEDED: "AI 예산 한도에 도달해 분석을 실행하지 못했습니다.",
  AI_REFUSED: "AI 제공자가 이 공고를 처리하지 않았습니다.",
  AI_OUTPUT_INVALID: "AI 응답이 검증을 통과하지 못했습니다. 다시 시도하세요.",
  AI_PROVIDER_UNAVAILABLE: "AI 제공자에 연결하지 못했습니다. 잠시 후 다시 시도하세요.",
  SNAPSHOT_NOT_FOUND: "스냅샷을 찾을 수 없습니다.",
};

/**
 * F04 entry point: enqueue posting analysis, poll the job, then refresh so drafts appear in
 * the review panel as 검토 필요. Long AI work is asynchronous by design (ADR-0004).
 */
export function AnalyzeRequirementsButton({ snapshotId }: { snapshotId?: string }) {
  const router = useRouter();
  const [phase, setPhase] = useState<Phase>({ kind: "idle" });
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  async function poll(jobId: string, since: number) {
    const { data, error } = await api.GET("/jobs/{id}", { params: { path: { id: jobId } } });
    if (error || !data) {
      setPhase({ kind: "failed", message: error?.detail ?? "작업 상태를 확인하지 못했습니다." });
      return;
    }
    if (data.status === "SUCCEEDED") {
      const result = (data.result ?? {}) as { extracted?: number; withoutSpan?: number };
      setPhase({
        kind: "done",
        extracted: result.extracted ?? 0,
        withoutSpan: result.withoutSpan ?? 0,
      });
      router.refresh();
      return;
    }
    if (data.status === "FAILED" || data.status === "CANCELLED") {
      const code = data.errorCode ?? "";
      setPhase({
        kind: "failed",
        message: ERROR_MESSAGES[code] ?? `분석에 실패했습니다 (${code || data.status}).`,
      });
      return;
    }
    if (Date.now() - since > TIMEOUT_MS) {
      setPhase({
        kind: "failed",
        message: "분석이 예상보다 오래 걸립니다. 잠시 후 페이지를 새로고침하세요.",
      });
      return;
    }
    timer.current = setTimeout(() => poll(jobId, since), POLL_MS);
  }

  async function start() {
    if (!snapshotId) return;
    setPhase({ kind: "idle" });
    const { data, error } = await api.POST("/job-posting-snapshots/{id}/analysis-jobs", {
      params: { path: { id: snapshotId } },
    });
    if (error || !data) {
      setPhase({ kind: "failed", message: error?.detail ?? "분석을 시작하지 못했습니다." });
      return;
    }
    const since = Date.now();
    setPhase({ kind: "queued", jobId: data.jobId, since });
    timer.current = setTimeout(() => poll(data.jobId, since), POLL_MS);
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button
        variant="secondary"
        onClick={start}
        loading={phase.kind === "queued"}
        disabled={!snapshotId}
        title={
          !snapshotId
            ? "저장된 본문이 필요합니다"
            : "AI가 원문에서 요구사항 초안을 뽑아 검토 목록에 넣습니다"
        }
      >
        <Icon name="clipboard" size={16} />
        요구사항 분석
      </Button>
      {phase.kind === "queued" ? (
        <span role="status" className="text-caption text-text-600">
          분석 중… 결과는 검토 필요 상태로 들어옵니다.
        </span>
      ) : null}
      {phase.kind === "done" ? (
        <span role="status" className="text-caption text-success-700">
          초안 {phase.extracted}개 추가됨
          {phase.withoutSpan > 0 ? ` (원문 위치 미확인 ${phase.withoutSpan}개)` : ""}. 아래에서
          승인하세요.
        </span>
      ) : null}
      {phase.kind === "failed" ? (
        <span role="alert" className="text-caption text-warning-700">
          {phase.message}
        </span>
      ) : null}
    </div>
  );
}
