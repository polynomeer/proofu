"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { careerEntryTypeLabel } from "@/lib/labels";

type Proposal = Schema<"CareerImportProposal">;

type Outcome = { created: number; failed: number };

const MAX_CHARS = 50_000;

function period(proposal: Proposal) {
  return `${proposal.startDate} – ${proposal.endDate ?? "현재"}`;
}

/**
 * F13: paste a resume, compare each proposal with the line it came from, and create the ones
 * you keep. Nothing is stored until then — the proposals live in the job result only, and a
 * created entry is an ordinary career entry from that point on.
 */
export function CareerImport() {
  const router = useRouter();
  const [text, setText] = useState("");
  const [proposals, setProposals] = useState<Proposal[] | null>(null);
  const [dropped, setDropped] = useState<string[]>([]);
  const [redacted, setRedacted] = useState(0);
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [applying, setApplying] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  function toggle(index: number) {
    setPicked((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  }

  async function start() {
    setProposals(null);
    setOutcome(null);
    setProblem(null);
    const { data, error } = await api.POST("/career-imports", { body: { text } });
    return { jobId: data?.jobId, error: error?.detail };
  }

  function receive(result: Record<string, unknown>) {
    const items = (result.proposals ?? []) as Proposal[];
    setProposals(items);
    setDropped((result.dropped ?? []) as string[]);
    setRedacted(Number(result.redacted ?? 0));
    setPicked(new Set(items.map((_, index) => index)));
  }

  async function apply() {
    if (!proposals) return;
    setApplying(true);
    setProblem(null);
    const chosen = [...picked].sort((a, b) => a - b);
    let created = 0;
    let failed = 0;
    const kept: Proposal[] = [];
    for (const index of chosen) {
      const proposal = proposals[index];
      if (!proposal) continue;
      const { error } = await api.POST("/career-entries", {
        body: {
          type: proposal.type,
          title: proposal.title,
          organization: proposal.organization ?? undefined,
          location: proposal.location ?? undefined,
          description: proposal.description ?? undefined,
          startDate: proposal.startDate,
          endDate: proposal.endDate ?? undefined,
        },
      });
      if (error) {
        failed += 1;
        kept.push(proposal);
      } else {
        created += 1;
      }
    }
    // What was created is gone from the list; what failed stays so it can be tried again.
    const untouched = proposals.filter((_, index) => !picked.has(index));
    const remaining = [...kept, ...untouched];
    setProposals(remaining);
    setPicked(new Set(remaining.map((_, index) => index)));
    setOutcome({ created, failed });
    if (failed > 0)
      setProblem("일부 기록을 추가하지 못했습니다. 남은 항목을 다시 시도하거나 직접 입력하세요.");
    setApplying(false);
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-2">
        <label htmlFor="resume" className="text-body font-semibold">
          이력서 원문
        </label>
        <p className="text-caption text-text-600">
          이력서를 그대로 붙여넣으세요. 이메일·전화번호 같은 연락처는 분석에 보내기 전에 서버가
          지웁니다. 원문과 추출 결과는 저장되지 않고, 아래에서 선택한 기록만 추가됩니다.
        </p>
        <textarea
          id="resume"
          className={textareaClass}
          rows={12}
          maxLength={MAX_CHARS}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={
            "2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어\n결제 시스템을 재작성했습니다."
          }
        />
        <div className="flex items-center justify-between gap-3">
          <span className="text-caption text-text-600">
            {text.length.toLocaleString()} / {MAX_CHARS.toLocaleString()}자
          </span>
          <AsyncJobButton
            variant="primary"
            start={start}
            label="경력 찾기"
            runningText="이력서를 읽는 중입니다…"
            disabled={text.trim().length < 10}
            title={text.trim().length < 10 ? "이력서 원문을 붙여넣으세요" : undefined}
            summary={(result) =>
              `${((result.proposals ?? []) as Proposal[]).length}건을 찾았습니다.`
            }
            onDone={receive}
            refreshOnDone={false}
          />
        </div>
      </section>

      {problem ? <ErrorState title="추가하지 못한 기록이 있습니다" description={problem} /> : null}

      {outcome ? (
        <p role="status" className="text-body text-success-700">
          {outcome.created}건을 추가했습니다.{" "}
          <Link href="/career" className="text-primary-600 underline">
            커리어에서 보기
          </Link>
        </p>
      ) : null}

      {proposals ? (
        proposals.length === 0 ? (
          // After applying everything the list is empty because the user emptied it; saying
          // "nothing was found" there would contradict the line above it.
          outcome ? null : (
            <p className="text-body text-text-600">
              경력 기록을 찾지 못했습니다. 기간과 회사·직무가 한 줄에 들어 있는 형식이면 더 잘
              찾습니다.
            </p>
          )
        ) : (
          <section className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-card-title">찾은 경력 {proposals.length}건</h2>
              <span className="text-caption text-text-600">
                {redacted > 0 ? `연락처 ${redacted}곳을 지우고 분석했습니다. ` : ""}
                추가한 뒤 각 기록에서 수정할 수 있습니다.
              </span>
            </div>
            <ul className="flex flex-col gap-3">
              {proposals.map((proposal, index) => (
                <li
                  key={`${proposal.title}-${proposal.startDate}-${index}`}
                  className="rounded-md border border-border-300 bg-surface-000 p-4"
                >
                  <label className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      className="mt-1 h-4 w-4"
                      checked={picked.has(index)}
                      onChange={() => toggle(index)}
                    />
                    <span className="flex min-w-0 flex-1 flex-col gap-1">
                      <span className="text-body font-semibold">{proposal.title}</span>
                      <span className="text-caption text-text-600">
                        {[
                          careerEntryTypeLabel(proposal.type),
                          proposal.organization,
                          period(proposal),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                      {proposal.description ? (
                        <span className="text-caption text-text-600">{proposal.description}</span>
                      ) : null}
                    </span>
                  </label>
                  <blockquote className="mt-3 border-l-2 border-border-300 pl-3 text-caption text-text-600">
                    {proposal.quote}
                  </blockquote>
                  {proposal.warning ? (
                    <p className="mt-2 text-caption text-warning-700">{proposal.warning}</p>
                  ) : null}
                </li>
              ))}
            </ul>
            <div className="flex justify-end">
              <Button onClick={apply} loading={applying} disabled={picked.size === 0}>
                선택한 {picked.size}건 추가
              </Button>
            </div>
          </section>
        )
      ) : null}

      {dropped.length > 0 ? (
        <section className="flex flex-col gap-1">
          <h2 className="text-caption font-semibold text-text-600">
            넣지 않은 항목 {dropped.length}건
          </h2>
          <ul className="flex flex-col gap-1 text-caption text-text-600">
            {dropped.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
