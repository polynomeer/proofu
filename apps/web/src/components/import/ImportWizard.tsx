"use client";

import { useState, type ChangeEvent } from "react";

import type { Schema } from "@proofu/contracts";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { IMPORT_ITEM_TYPES, importItemStatusLabel, importItemTypeLabel } from "@/lib/labels";

type Bundle = Schema<"ImportBundle">;
type Run = Schema<"ImportRun">;
type Item = Schema<"ImportItem">;
type Status = Schema<"ImportItemStatus">;

type Outcome = { applied: number; skipped: Record<string, string> };

/** The verdicts worth acting on first; the rest are there to be read. */
const ORDER: Status[] = ["NEW", "CHANGED", "CONFLICT", "REJECTED", "UNCHANGED"];

/** Only these can be created in this stage, so only these are selectable. */
const PICKABLE: Status[] = ["NEW"];

const TONE: Record<Status, "snapshot" | "review" | "expired" | "neutral"> = {
  NEW: "snapshot",
  CHANGED: "review",
  CONFLICT: "review",
  REJECTED: "expired",
  UNCHANGED: "neutral",
};

function parseBundle(text: string): { bundle?: Bundle; count?: number; error?: string } {
  if (!text.trim()) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { error: "JSON으로 읽을 수 없습니다." };
  }
  const bundle = parsed as Bundle;
  if (!Array.isArray(bundle?.items) || bundle.items.length === 0) {
    return { error: "items 배열이 비어 있습니다." };
  }
  return { bundle, count: bundle.items.length };
}

function countOf(run: Run, status: Status): number {
  return run.summary[status] ?? 0;
}

/**
 * F21 1단계: plan a bundle, read every verdict, create only what was picked
 * (docs/product/personal-archive-import.md §흐름).
 *
 * The bundle stays in this screen. Nothing of it is stored on the server between planning and
 * applying — the ledger holds keys, hashes and verdicts only — so applying sends it again, and
 * leaving the page means choosing the file once more. That is the price of not keeping a copy of
 * someone's archive, and the screen says so instead of letting it surprise anyone.
 */
export function ImportWizard() {
  const [text, setText] = useState("");
  const [run, setRun] = useState<Run | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const parsed = parseBundle(text);

  function reset(next: string) {
    setText(next);
    setRun(null);
    setOutcome(null);
    setProblem(null);
  }

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    file
      .text()
      .then(reset)
      .catch(() => setProblem("파일을 읽지 못했습니다."));
  }

  async function startPlan() {
    if (!parsed.bundle) return { error: "번들을 먼저 고르세요." };
    setRun(null);
    setOutcome(null);
    setProblem(null);
    const { data, error } = await api.POST("/imports", { body: parsed.bundle });
    return { jobId: data?.jobId, error: error?.detail };
  }

  async function receivePlan(result: Record<string, unknown>) {
    const runId = typeof result.runId === "string" ? result.runId : "";
    if (!runId) {
      setProblem("계획을 세웠지만 결과를 읽지 못했습니다.");
      return;
    }
    const { data, error } = await api.GET("/imports/{id}", { params: { path: { id: runId } } });
    if (!data) {
      setProblem(error?.detail ?? "계획을 불러오지 못했습니다.");
      return;
    }
    setRun(data);
    // Only what is actually new starts selected: a few hundred rows is not a checklist to clear.
    setPicked(new Set(data.items.filter(canPick).map((item) => item.sourceKey)));
  }

  async function startApply() {
    if (!run || !parsed.bundle) return { error: "계획이 없습니다. 다시 계획하세요." };
    setProblem(null);
    const { data, error } = await api.POST("/imports/{id}/apply", {
      params: { path: { id: run.id } },
      body: { sourceKeys: [...picked], bundle: parsed.bundle },
    });
    return { jobId: data?.jobId, error: error?.detail };
  }

  async function receiveApply(result: Record<string, unknown>) {
    const applied = (result.applied ?? {}) as Record<string, string>;
    const skipped = (result.skipped ?? {}) as Record<string, string>;
    setOutcome({ applied: Object.keys(applied).length, skipped });
    setPicked(new Set());
    // The table would otherwise keep offering rows that are already rows: the ledger now says
    // which keys were applied, so it is read back rather than guessed from the result.
    if (!run) return;
    const { data } = await api.GET("/imports/{id}", { params: { path: { id: run.id } } });
    if (data) setRun(data);
  }

  function toggle(key: string) {
    setPicked((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function toggleAll(items: Item[], on: boolean) {
    setPicked((current) => {
      const next = new Set(current);
      items.filter(canPick).forEach((item) => {
        if (on) next.add(item.sourceKey);
        else next.delete(item.sourceKey);
      });
      return next;
    });
  }

  const groups = IMPORT_ITEM_TYPES.map((type) => ({
    type,
    items: (run?.items ?? []).filter((item) => item.type === type),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="flex flex-col gap-4">
      {problem ? <ErrorState title="들여올 수 없습니다" description={problem} /> : null}

      <section
        aria-labelledby="bundle-heading"
        className="flex flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6"
      >
        <div>
          <h2 id="bundle-heading" className="text-card-title">
            번들
          </h2>
          <p className="mt-1 max-w-prose text-body text-text-600">
            어댑터가 만든 JSON을 고르거나 붙여넣으세요. 번들은 이 화면에만 있습니다 — 서버는
            보관하지 않으므로 페이지를 떠나면 파일을 다시 골라야 합니다.
          </p>
        </div>
        <Field id="bundle-file" label="파일에서">
          <input
            id="bundle-file"
            type="file"
            accept="application/json,.json"
            className="text-body"
            onChange={onFile}
          />
        </Field>
        <Field
          id="bundle-text"
          label="또는 붙여넣기"
          help={parsed.count ? `${parsed.count}건을 읽었습니다` : undefined}
          error={parsed.error}
        >
          <textarea
            id="bundle-text"
            className={textareaClass}
            rows={6}
            value={text}
            aria-invalid={parsed.error ? true : undefined}
            aria-describedby={parsed.error ? "bundle-text-error" : undefined}
            onChange={(event) => reset(event.target.value)}
            placeholder={'{"source":"career-hub","items":[…]}'}
          />
        </Field>
        {/* The button lays its status line out right-aligned, so it is kept to its own width. */}
        <div className="w-fit">
          <AsyncJobButton
            start={startPlan}
            label="무엇이 들어올지 보기"
            runningText="계획을 세우는 중"
            summary={(result) => {
              const summary = (result.summary ?? {}) as Record<string, number>;
              return `새로 들일 수 있는 항목 ${summary.NEW ?? 0}건`;
            }}
            disabled={!parsed.bundle}
            onDone={receivePlan}
            refreshOnDone={false}
            variant="primary"
          />
        </div>
      </section>

      {run ? (
        <section
          aria-labelledby="plan-heading"
          className="flex flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6"
        >
          <div>
            <h2 id="plan-heading" className="text-card-title">
              계획
            </h2>
            <p className="mt-1 text-body text-text-600">
              {run.itemCount}건을 살펴봤습니다. 고른 것만 만들어지고, 나머지는 아무것도 바뀌지
              않습니다.
            </p>
            <ul className="mt-2 flex flex-wrap gap-2">
              {ORDER.filter((status) => countOf(run, status) > 0).map((status) => (
                <li key={status}>
                  <Chip tone={TONE[status]}>
                    {importItemStatusLabel(status)} {countOf(run, status)}
                  </Chip>
                </li>
              ))}
            </ul>
          </div>

          {groups.map((group) => (
            <div key={group.type}>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <h3 className="text-section-title">{importItemTypeLabel(group.type)}</h3>
                <span className="text-caption text-text-600">{group.items.length}건</span>
                {group.items.some(canPick) ? (
                  <>
                    <Button
                      variant="tertiary"
                      className="h-8 px-2"
                      onClick={() => toggleAll(group.items, true)}
                    >
                      모두 선택
                    </Button>
                    <Button
                      variant="tertiary"
                      className="h-8 px-2"
                      onClick={() => toggleAll(group.items, false)}
                    >
                      모두 해제
                    </Button>
                  </>
                ) : null}
              </div>
              <ul className="mt-1 divide-y divide-border-300">
                {group.items.map((item) => (
                  <li key={item.id} className="py-2">
                    <div className="flex items-start gap-2">
                      <input
                        id={`item-${item.id}`}
                        type="checkbox"
                        className="mt-1.5 h-4 w-4 shrink-0"
                        checked={picked.has(item.sourceKey)}
                        disabled={!canPick(item)}
                        onChange={() => toggle(item.sourceKey)}
                      />
                      <label htmlFor={`item-${item.id}`} className="min-w-0 text-body">
                        <span className="block break-all">{item.sourceKey}</span>
                        <span className="mt-1 flex flex-wrap items-center gap-2">
                          {item.appliedAt ? (
                            <Chip tone="verified">들여옴</Chip>
                          ) : (
                            <Chip tone={TONE[item.status]}>
                              {importItemStatusLabel(item.status)}
                            </Chip>
                          )}
                          {item.sourcePath && item.sourcePath !== item.sourceKey ? (
                            <span className="break-all text-caption text-text-600">
                              {item.sourcePath}
                            </span>
                          ) : null}
                          {item.reason ? (
                            <span className="text-caption text-text-600">{item.reason}</span>
                          ) : null}
                        </span>
                      </label>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          <div className="flex flex-col gap-2">
            <div className="w-fit">
              <AsyncJobButton
                start={startApply}
                label={`고른 ${picked.size}건 들이기`}
                runningText="만드는 중"
                summary={(result) =>
                  `${Object.keys((result.applied ?? {}) as Record<string, string>).length}건 생성`
                }
                disabled={picked.size === 0}
                onDone={receiveApply}
                variant="primary"
              />
            </div>
            <p className="max-w-prose text-caption text-text-600">
              이 단계는 새 항목만 들입니다. 이미 들여온 것, 원본이 바뀐 것, 들일 수 없는 것은 고를
              수 없습니다.
            </p>
          </div>
        </section>
      ) : null}

      {outcome ? (
        <section
          aria-labelledby="outcome-heading"
          className="flex flex-col gap-2 rounded-md border border-border-300 bg-surface-000 p-4 md:p-6"
        >
          <h2 id="outcome-heading" className="text-card-title">
            결과
          </h2>
          <p className="text-body">{outcome.applied}건이 들어왔습니다.</p>
          {Object.keys(outcome.skipped).length > 0 ? (
            <>
              <p className="text-body text-text-600">들이지 않은 항목:</p>
              <ul className="flex flex-col gap-1">
                {Object.entries(outcome.skipped).map(([key, reason]) => (
                  <li key={key} className="text-caption text-text-600">
                    <span className="font-semibold text-text-900">{key}</span> — {reason}
                  </li>
                ))}
              </ul>
            </>
          ) : null}
          <p className="max-w-prose text-caption text-text-600">
            같은 번들을 다시 계획하면 이미 들여온 항목은 &ldquo;이미 들여옴&rdquo;으로 나옵니다.
          </p>
        </section>
      ) : null}
    </div>
  );
}

/** Already a row of its own, or a verdict this stage cannot act on. */
function canPick(item: Item): boolean {
  return !item.appliedAt && PICKABLE.includes(item.status);
}
