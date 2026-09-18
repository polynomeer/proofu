"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";

import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";

type Phase =
  | { kind: "idle" }
  | { kind: "queued"; jobId: string; since: number }
  | { kind: "done"; result: Record<string, unknown> }
  | { kind: "failed"; message: string };

const POLL_MS = 1500;
const TIMEOUT_MS = 120_000;

export const AI_JOB_ERRORS: Record<string, string> = {
  AI_BUDGET_EXCEEDED: "AI 예산 한도에 도달해 실행하지 못했습니다.",
  AI_REFUSED: "AI 제공자가 이 요청을 처리하지 않았습니다.",
  AI_OUTPUT_INVALID: "AI 응답이 검증을 통과하지 못했습니다. 다시 시도하세요.",
  AI_PROVIDER_UNAVAILABLE: "AI 제공자에 연결하지 못했습니다. 잠시 후 다시 시도하세요.",
};

/**
 * Starts an asynchronous job (202 + jobId), polls GET /jobs/{id} and refreshes the page on
 * success (ADR-0004). `start` returns the job id; `summary` turns the job result into a line.
 */
export function AsyncJobButton({
  start,
  label,
  icon,
  runningText,
  summary,
  errors = {},
  disabled,
  title,
  variant = "secondary",
}: {
  start: () => Promise<{ jobId?: string; error?: string }>;
  label: string;
  icon?: ReactNode;
  runningText: string;
  summary: (result: Record<string, unknown>) => string;
  errors?: Record<string, string>;
  disabled?: boolean;
  title?: string;
  variant?: "primary" | "secondary";
}) {
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
      setPhase({ kind: "done", result: (data.result ?? {}) as Record<string, unknown> });
      router.refresh();
      return;
    }
    if (data.status === "FAILED" || data.status === "CANCELLED") {
      const code = data.errorCode ?? "";
      setPhase({
        kind: "failed",
        message:
          errors[code] ?? AI_JOB_ERRORS[code] ?? `실행에 실패했습니다 (${code || data.status}).`,
      });
      return;
    }
    if (Date.now() - since > TIMEOUT_MS) {
      setPhase({
        kind: "failed",
        message: "예상보다 오래 걸립니다. 잠시 후 페이지를 새로고침하세요.",
      });
      return;
    }
    timer.current = setTimeout(() => poll(jobId, since), POLL_MS);
  }

  async function run() {
    setPhase({ kind: "idle" });
    const { jobId, error } = await start();
    if (!jobId) {
      setPhase({ kind: "failed", message: error ?? "작업을 시작하지 못했습니다." });
      return;
    }
    const since = Date.now();
    setPhase({ kind: "queued", jobId, since });
    timer.current = setTimeout(() => poll(jobId, since), POLL_MS);
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button
        variant={variant}
        onClick={run}
        loading={phase.kind === "queued"}
        disabled={disabled}
        title={title}
      >
        {icon}
        {label}
      </Button>
      {phase.kind === "queued" ? (
        <span role="status" className="text-caption text-text-600">
          {runningText}
        </span>
      ) : null}
      {phase.kind === "done" ? (
        <span role="status" className="text-caption text-success-700">
          {summary(phase.result)}
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
