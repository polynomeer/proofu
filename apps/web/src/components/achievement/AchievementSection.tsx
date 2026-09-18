"use client";

import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { ClaimPanel } from "@/components/claim/ClaimPanel";
import { api } from "@/lib/api";

type Achievement = Schema<"Achievement">;
type Claim = Schema<"Claim">;
type Input = Schema<"AchievementInput">;

/**
 * User-stated confidence in the outcome. Three bands keep the input honest; the
 * numeric value is what the API stores (docs/domain/career-data-model.md).
 */
const CONFIDENCE_BANDS = [
  { value: 0.9, label: "높음 — 기록·지표로 확인 가능" },
  { value: 0.6, label: "보통 — 기억과 정황에 근거" },
  { value: 0.3, label: "낮음 — 추정" },
] as const;

function nearestBand(value: number): number {
  return CONFIDENCE_BANDS.reduce((best, b) =>
    Math.abs(b.value - value) < Math.abs(best.value - value) ? b : best,
  ).value;
}

const EMPTY: Input = { action: "", outcome: "", confidence: 0.6 };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

function AchievementForm({
  projectId,
  initial,
  onSaved,
  onCancel,
}: {
  projectId: string;
  initial?: Achievement;
  onSaved: (a: Achievement) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Input>(
    initial ? { ...initial, confidence: nearestBand(initial.confidence) } : EMPTY,
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const prefix = initial ? `a-${initial.id}` : "a-new";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      metricValue: values.metricValue ?? null,
      metricUnit: values.metricUnit || null,
      baseline: values.baseline || null,
      timeframe: values.timeframe || null,
    };
    const result = initial
      ? await api.PATCH("/achievements/{id}", {
          params: { path: { id: initial.id } },
          body: { ...body, revision: initial.revision },
        })
      : await api.POST("/projects/{id}/achievements", {
          params: { path: { id: projectId } },
          body,
        });
    setSubmitting(false);
    if (result.error) {
      const errors = fieldErrorsOf(result.error);
      setFieldErrors(errors);
      setProblem(
        result.error.code === "DOMAIN_RULE_VIOLATION"
          ? "수치를 입력했다면 단위도 함께 입력하세요."
          : result.error.code === "CONFLICT_STALE_VERSION"
            ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요."
            : Object.keys(errors).length > 0
              ? "표시된 항목을 확인하세요."
              : (result.error.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    onSaved(result.data);
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="flex flex-col gap-4 rounded-md border border-primary-600/40 bg-surface-050 p-4"
    >
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <Field
        id={`${prefix}-action`}
        label="행동"
        required
        help="무엇을 했는지"
        error={fieldErrors.action}
      >
        <input
          id={`${prefix}-action`}
          className={inputClass}
          value={values.action}
          aria-invalid={fieldErrors.action ? true : undefined}
          onChange={(e) => set("action", e.target.value)}
        />
      </Field>
      <Field
        id={`${prefix}-outcome`}
        label="결과"
        required
        help="무엇이 달라졌는지"
        error={fieldErrors.outcome}
      >
        <textarea
          id={`${prefix}-outcome`}
          className={textareaClass}
          value={values.outcome}
          aria-invalid={fieldErrors.outcome ? true : undefined}
          onChange={(e) => set("outcome", e.target.value)}
        />
      </Field>
      <div className="grid gap-4 sm:grid-cols-4">
        <Field id={`${prefix}-metricValue`} label="수치" error={fieldErrors.metricValue}>
          <input
            id={`${prefix}-metricValue`}
            type="number"
            step="any"
            className={inputClass}
            value={values.metricValue ?? ""}
            onChange={(e) =>
              set("metricValue", e.target.value === "" ? null : Number(e.target.value))
            }
          />
        </Field>
        <Field
          id={`${prefix}-metricUnit`}
          label="단위"
          help="수치가 있으면 필수"
          error={fieldErrors.metricUnit}
        >
          <input
            id={`${prefix}-metricUnit`}
            className={inputClass}
            placeholder="%, ms, 건"
            maxLength={40}
            value={values.metricUnit ?? ""}
            onChange={(e) => set("metricUnit", e.target.value)}
          />
        </Field>
        <Field
          id={`${prefix}-baseline`}
          label="기준"
          help="변경 전 값"
          error={fieldErrors.baseline}
        >
          <input
            id={`${prefix}-baseline`}
            className={inputClass}
            maxLength={200}
            value={values.baseline ?? ""}
            onChange={(e) => set("baseline", e.target.value)}
          />
        </Field>
        <Field id={`${prefix}-timeframe`} label="기간" error={fieldErrors.timeframe}>
          <input
            id={`${prefix}-timeframe`}
            className={inputClass}
            placeholder="2024 Q3"
            maxLength={120}
            value={values.timeframe ?? ""}
            onChange={(e) => set("timeframe", e.target.value)}
          />
        </Field>
      </div>
      <Field
        id={`${prefix}-confidence`}
        label="신뢰도"
        required
        help="이 결과를 얼마나 확실히 말할 수 있는지"
        error={fieldErrors.confidence}
      >
        <select
          id={`${prefix}-confidence`}
          className={inputClass}
          value={values.confidence}
          onChange={(e) => set("confidence", Number(e.target.value))}
        >
          {CONFIDENCE_BANDS.map((b) => (
            <option key={b.value} value={b.value}>
              {b.label}
            </option>
          ))}
        </select>
      </Field>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "변경 사항 저장" : "성과 추가"}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function confidenceLabel(value: number): string {
  const band = nearestBand(value);
  return band >= 0.9 ? "신뢰도 높음" : band >= 0.6 ? "신뢰도 보통" : "신뢰도 낮음";
}

function claimText(item: Achievement): string {
  const metric = item.metricValue != null ? ` (${item.metricValue}${item.metricUnit ?? ""})` : "";
  return `${item.action} — ${item.outcome}${metric}`;
}

function AchievementRow({
  item,
  claims,
  onEdit,
  onDeleted,
}: {
  item: Achievement;
  claims: Claim[];
  onEdit: () => void;
  onDeleted: () => void;
}) {
  const [busy, setBusy] = useState(false);
  async function remove() {
    if (
      !window.confirm(
        `"${item.action}" 성과를 삭제할까요?\n30일 동안 휴지통에 보관된 뒤 영구 삭제됩니다.`,
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/achievements/{id}", { params: { path: { id: item.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }
  return (
    <li className="flex flex-col gap-3 px-4 py-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-card-title">{item.action}</p>
          <p className="mt-1 text-body text-text-900">{item.outcome}</p>
          <p className="mt-1 flex flex-wrap gap-x-3 text-caption text-text-600 tabular-nums">
            {item.metricValue != null ? (
              <span className="font-semibold text-text-900">
                {item.metricValue}
                {item.metricUnit}
                {item.baseline ? ` (기준 ${item.baseline})` : ""}
              </span>
            ) : null}
            {item.timeframe ? <span>{item.timeframe}</span> : null}
            <span>{confidenceLabel(item.confidence)}</span>
          </p>
        </div>
        <div className="flex shrink-0 gap-1">
          <Button type="button" variant="tertiary" onClick={onEdit}>
            편집
          </Button>
          <Button type="button" variant="tertiary" onClick={remove} loading={busy}>
            삭제
          </Button>
        </div>
      </div>
      <ClaimPanel
        source={{ type: "ACHIEVEMENT", id: item.id }}
        initialClaims={claims}
        defaultText={claimText(item)}
      />
    </li>
  );
}

/** Achievements of one project with inline add/edit/delete. State is local; the server list is the initial value. */
export function AchievementSection({
  projectId,
  initialItems,
  initialClaims,
}: {
  projectId: string;
  initialItems: Achievement[];
  /** Claims about this project's achievements, grouped here by source id. */
  initialClaims: Claim[];
}) {
  const [items, setItems] = useState(initialItems);
  const [editing, setEditing] = useState<"new" | string | null>(null);

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-section-title">성과</h2>
        {editing === null ? (
          <Button type="button" variant="secondary" onClick={() => setEditing("new")}>
            <Icon name="plus" size={16} />
            성과 추가
          </Button>
        ) : null}
      </div>
      <p className="text-caption text-text-600">
        행동이 아니라 결과를 적습니다. 수치는 나중에 Evidence로 뒷받침되기 전까지 사용자 진술로
        취급됩니다.
      </p>

      {editing === "new" ? (
        <AchievementForm
          projectId={projectId}
          onSaved={(a) => {
            setItems((list) => [...list, a]);
            setEditing(null);
          }}
          onCancel={() => setEditing(null)}
        />
      ) : null}

      {items.length === 0 && editing !== "new" ? (
        <EmptyState
          title="아직 성과가 없습니다"
          description="이 프로젝트에서 무엇이 달라졌는지 한 가지를 기록해 보세요."
        />
      ) : (
        <ul className="divide-y divide-border-300 rounded-md border border-border-300 bg-surface-000">
          {items.map((item) =>
            editing === item.id ? (
              <li key={item.id} className="p-2">
                <AchievementForm
                  projectId={projectId}
                  initial={item}
                  onSaved={(a) => {
                    setItems((list) => list.map((x) => (x.id === a.id ? a : x)));
                    setEditing(null);
                  }}
                  onCancel={() => setEditing(null)}
                />
              </li>
            ) : (
              <AchievementRow
                key={item.id}
                item={item}
                claims={initialClaims.filter((c) =>
                  c.sources.some((s) => s.type === "ACHIEVEMENT" && s.id === item.id),
                )}
                onEdit={() => setEditing(item.id)}
                onDeleted={() => setItems((list) => list.filter((x) => x.id !== item.id))}
              />
            ),
          )}
        </ul>
      )}
    </section>
  );
}
