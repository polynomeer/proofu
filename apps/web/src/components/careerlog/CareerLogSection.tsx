"use client";

import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { LoadMore } from "@/components/ui/LoadMore";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { PAGE_LIMIT } from "@/lib/limits";
import { SENSITIVITIES, sensitivityLabel } from "@/lib/labels";

type CareerLog = Schema<"CareerLog">;
type Input = Schema<"CareerLogInput">;
type Sensitivity = Schema<"Sensitivity">;
type CareerEntry = Schema<"CareerEntry">;
type Project = Schema<"Project">;

/** What the user picks a confidence from when a log becomes an achievement. */
const CONFIDENCE_BANDS = [
  { value: 0.9, label: "높음 — 수치나 산출물로 보여줄 수 있음" },
  { value: 0.6, label: "보통 — 정황상 설명할 수 있음" },
  { value: 0.3, label: "낮음 — 기억에 의존" },
] as const;

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function LinkPicker({
  prefix,
  label,
  help,
  options,
  selected,
  onChange,
}: {
  prefix: string;
  label: string;
  help: string;
  options: { id: string; name: string }[];
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  if (options.length === 0) return null;
  return (
    <fieldset className="min-w-0">
      <legend className="text-caption font-semibold text-text-700">{label}</legend>
      <p className="text-caption text-text-600">{help}</p>
      <ul className="mt-2 flex max-h-40 flex-col gap-1 overflow-y-auto">
        {options.map((o) => (
          <li key={o.id}>
            <label htmlFor={`${prefix}-${o.id}`} className="flex items-center gap-2 text-body">
              <input
                id={`${prefix}-${o.id}`}
                type="checkbox"
                aria-label={o.name}
                checked={selected.includes(o.id)}
                onChange={(e) =>
                  onChange(
                    e.target.checked ? [...selected, o.id] : selected.filter((id) => id !== o.id),
                  )
                }
              />
              <span className="truncate">{o.name}</span>
            </label>
          </li>
        ))}
      </ul>
    </fieldset>
  );
}

function CareerLogForm({
  initial,
  entries,
  projects,
  onSaved,
  onCancel,
}: {
  initial?: CareerLog;
  entries: { id: string; name: string }[];
  projects: { id: string; name: string }[];
  onSaved: (log: CareerLog) => void;
  onCancel?: () => void;
}) {
  const [occurredAt, setOccurredAt] = useState(initial?.occurredAt ?? today());
  const [body, setBody] = useState(initial?.body ?? "");
  const [sensitivity, setSensitivity] = useState<Sensitivity>(
    initial?.sensitivity ?? "CONFIDENTIAL",
  );
  const [careerEntryIds, setCareerEntryIds] = useState<string[]>(initial?.careerEntryIds ?? []);
  const [projectIds, setProjectIds] = useState<string[]>(initial?.projectIds ?? []);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const prefix = initial ? `log-${initial.id}` : "log-new";

  function reset() {
    setOccurredAt(today());
    setBody("");
    setSensitivity("CONFIDENTIAL");
    setCareerEntryIds([]);
    setProjectIds([]);
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const payload: Input = {
      occurredAt,
      body: body.trim(),
      sensitivity,
      careerEntryIds,
      projectIds,
    };
    const { data, error } = initial
      ? await api.PATCH("/career-logs/{id}", {
          params: { path: { id: initial.id } },
          body: { ...payload, revision: initial.revision },
        })
      : await api.POST("/career-logs", { body: payload });
    setSubmitting(false);
    if (!data) {
      setFieldErrors(fieldErrorsOf(error));
      setProblem(
        error?.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요."
          : error?.code === "DOMAIN_RULE_VIOLATION"
            ? "일어난 날은 오늘보다 뒤일 수 없습니다."
            : error?.code === "NOT_FOUND"
              ? "연결하려는 경력이나 프로젝트를 찾을 수 없습니다."
              : (error?.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    if (!initial) reset();
    onSaved(data);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <Field
        id={`${prefix}-body`}
        label="무슨 일이 있었나"
        required
        error={fieldErrors.body}
        help="한두 문장이면 됩니다. 나중에 성과로 다듬을 재료입니다."
      >
        <textarea
          id={`${prefix}-body`}
          className={textareaClass}
          rows={3}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          aria-invalid={Boolean(fieldErrors.body)}
        />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id={`${prefix}-occurred`}
          label="일어난 날"
          required
          error={fieldErrors.occurredAt}
          help="오늘보다 뒤일 수 없습니다"
        >
          <input
            id={`${prefix}-occurred`}
            type="date"
            max={today()}
            className={inputClass}
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
            aria-invalid={Boolean(fieldErrors.occurredAt)}
          />
        </Field>
        <Field
          id={`${prefix}-sensitivity`}
          label="민감도"
          help="기본은 기밀입니다. 기밀·제한은 AI 컨텍스트에 들어가지 않습니다."
        >
          <select
            id={`${prefix}-sensitivity`}
            className={inputClass}
            value={sensitivity}
            onChange={(e) => setSensitivity(e.target.value as Sensitivity)}
          >
            {SENSITIVITIES.map((s) => (
              <option key={s} value={s}>
                {sensitivityLabel(s)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      {entries.length > 0 || projects.length > 0 ? (
        <details className="rounded-md border border-border-300 p-3">
          <summary className="cursor-pointer text-caption font-semibold text-text-700">
            어디서 있었던 일인지 연결 (선택)
          </summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            <LinkPicker
              prefix={`${prefix}-entry`}
              label="경력"
              help="이 일이 있었던 소속"
              options={entries}
              selected={careerEntryIds}
              onChange={setCareerEntryIds}
            />
            <LinkPicker
              prefix={`${prefix}-project`}
              label="프로젝트"
              help="이 일이 있었던 프로젝트"
              options={projects}
              selected={projectIds}
              onChange={setProjectIds}
            />
          </div>
        </details>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "저장" : "기록"}
        </Button>
        {onCancel ? (
          <Button type="button" variant="tertiary" onClick={onCancel}>
            취소
          </Button>
        ) : null}
      </div>
    </form>
  );
}

/**
 * Turns a log into an achievement: the achievement is created through its own endpoint and the
 * log is then marked as promoted. The copy is deliberate — afterwards the achievement is an
 * ordinary achievement and nothing on it says it came from a log.
 */
function PromoteForm({
  log,
  projects,
  onPromoted,
  onCancel,
}: {
  log: CareerLog;
  projects: { id: string; name: string }[];
  onPromoted: (log: CareerLog) => void;
  onCancel: () => void;
}) {
  const [projectId, setProjectId] = useState(projects[0]?.id ?? "");
  const [action, setAction] = useState(log.body);
  const [outcome, setOutcome] = useState("");
  const [confidence, setConfidence] = useState<number>(0.6);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const prefix = `promote-${log.id}`;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const created = await api.POST("/projects/{id}/achievements", {
      params: { path: { id: projectId } },
      body: { action: action.trim(), outcome: outcome.trim(), confidence },
    });
    if (!created.data) {
      setSubmitting(false);
      setFieldErrors(fieldErrorsOf(created.error));
      setProblem(created.error?.detail ?? "성과를 만들지 못했습니다.");
      return;
    }
    const marked = await api.POST("/career-logs/{id}/promotion", {
      params: { path: { id: log.id } },
      body: { targetType: "ACHIEVEMENT", targetId: created.data.id },
    });
    setSubmitting(false);
    if (!marked.data) {
      setProblem(
        "성과는 만들어졌지만 로그에 승격 기록을 남기지 못했습니다. 프로젝트에서 성과를 확인하세요.",
      );
      return;
    }
    onPromoted(marked.data);
  }

  if (projects.length === 0) {
    return (
      <div className="flex flex-col gap-2">
        <p className="text-caption text-text-600">
          성과는 프로젝트에 속합니다. 먼저 프로젝트를 만든 뒤 승격하세요.
        </p>
        <Button variant="tertiary" onClick={onCancel} className="self-start">
          닫기
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {problem ? <ErrorState title="승격할 수 없습니다" description={problem} /> : null}
      <p className="text-caption text-text-600">
        로그 내용을 성과로 옮깁니다. 저장하면 보통의 성과가 되고, 로그에는 승격했다는 사실만
        남습니다.
      </p>
      <Field id={`${prefix}-project`} label="프로젝트" required>
        <select
          id={`${prefix}-project`}
          className={inputClass}
          value={projectId}
          onChange={(e) => setProjectId(e.target.value)}
        >
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </Field>
      <Field
        id={`${prefix}-action`}
        label="한 일"
        required
        error={fieldErrors.action}
        help="로그에서 가져왔습니다. 필요하면 다듬으세요."
      >
        <textarea
          id={`${prefix}-action`}
          className={textareaClass}
          rows={2}
          value={action}
          onChange={(e) => setAction(e.target.value)}
          aria-invalid={Boolean(fieldErrors.action)}
        />
      </Field>
      <Field
        id={`${prefix}-outcome`}
        label="그래서 무엇이 바뀌었나"
        required
        error={fieldErrors.outcome}
        help="행동이 아니라 결과를 적습니다"
      >
        <textarea
          id={`${prefix}-outcome`}
          className={textareaClass}
          rows={2}
          value={outcome}
          onChange={(e) => setOutcome(e.target.value)}
          aria-invalid={Boolean(fieldErrors.outcome)}
        />
      </Field>
      <Field id={`${prefix}-confidence`} label="확신도" help="스스로 설명할 수 있는 정도입니다">
        <select
          id={`${prefix}-confidence`}
          className={inputClass}
          value={confidence}
          onChange={(e) => setConfidence(Number(e.target.value))}
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
          성과로 만들기
        </Button>
        <Button type="button" variant="tertiary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function CareerLogRow({
  log,
  entries,
  projects,
  onChanged,
  onDeleted,
}: {
  log: CareerLog;
  entries: { id: string; name: string }[];
  projects: { id: string; name: string }[];
  onChanged: (log: CareerLog) => void;
  onDeleted: () => void;
}) {
  const [mode, setMode] = useState<"view" | "edit" | "promote">("view");
  const [busy, setBusy] = useState(false);
  const linkNames = [
    ...(log.careerEntryIds ?? []).map((id) => entries.find((e) => e.id === id)?.name),
    ...(log.projectIds ?? []).map((id) => projects.find((p) => p.id === id)?.name),
  ].filter((name): name is string => Boolean(name));

  async function remove() {
    if (
      !window.confirm(
        "이 로그를 삭제할까요?\n휴지통에 보관된 뒤 설정한 보존 기간이 지나면 영구 삭제됩니다. 이미 승격한 성과는 그대로 남습니다.",
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/career-logs/{id}", { params: { path: { id: log.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }

  if (mode === "edit") {
    return (
      <li className="py-3">
        <CareerLogForm
          initial={log}
          entries={entries}
          projects={projects}
          onSaved={(next) => {
            onChanged(next);
            setMode("view");
          }}
          onCancel={() => setMode("view")}
        />
      </li>
    );
  }

  if (mode === "promote") {
    return (
      <li className="py-3">
        <PromoteForm
          log={log}
          projects={projects}
          onPromoted={(next) => {
            onChanged(next);
            setMode("view");
          }}
          onCancel={() => setMode("view")}
        />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-start gap-2 py-3">
      <div className="min-w-0 flex-1">
        <p className="text-body whitespace-pre-line">{log.body}</p>
        <span className="mt-1 flex flex-wrap items-center gap-2 text-caption text-text-600">
          <span>{formatDate(log.occurredAt)}</span>
          <Chip>{sensitivityLabel(log.sensitivity)}</Chip>
          {log.promotion ? <Chip>성과로 승격 · {formatDate(log.promotion.promotedAt)}</Chip> : null}
          {linkNames.length > 0 ? <span>{linkNames.join(" · ")}</span> : null}
        </span>
      </div>
      {!log.promotion ? (
        <Button
          variant="tertiary"
          className="h-8 px-2 text-caption"
          onClick={() => setMode("promote")}
        >
          성과로
        </Button>
      ) : null}
      <Button variant="tertiary" className="h-8 px-2 text-caption" onClick={() => setMode("edit")}>
        편집
      </Button>
      <Button variant="tertiary" className="h-8 px-2 text-caption" onClick={remove} loading={busy}>
        삭제
      </Button>
    </li>
  );
}

/**
 * F20 커리어 로그: written while the work happens so achievements are not reconstructed from
 * memory at application time. The capture form stays open — the whole point is that writing a
 * line costs nothing (docs/domain/career-data-model.md §커리어 로그).
 */
export function CareerLogSection({
  initialItems,
  initialCursor,
  promoted,
  entries,
  projects,
  pickersTruncated,
}: {
  initialItems: CareerLog[];
  initialCursor: string | null;
  promoted?: boolean;
  entries: CareerEntry[];
  projects: Project[];
  pickersTruncated: boolean;
}) {
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const entryOptions = entries.map((e) => ({ id: e.id, name: e.title }));
  const projectOptions = projects.map((p) => ({ id: p.id, name: p.name }));

  return (
    <section className="flex flex-col gap-4">
      <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
        <h2 className="text-card-title mb-4">오늘의 기록</h2>
        <CareerLogForm
          entries={entryOptions}
          projects={projectOptions}
          onSaved={(log) => setItems((list) => [log, ...list])}
        />
        {pickersTruncated ? (
          <p className="mt-3 text-caption text-text-600">
            연결 목록에는 최근 경력·프로젝트만 보여 줍니다. 찾는 항목이 없으면 커리어 목록에서
            확인하세요.
          </p>
        ) : null}
      </div>

      {items.length === 0 ? (
        <EmptyState
          title={
            promoted === undefined
              ? "아직 기록이 없습니다"
              : promoted
                ? "승격한 기록이 없습니다"
                : "승격을 기다리는 기록이 없습니다"
          }
          description="그때그때 한 줄씩 적어 두면, 지원할 때 성과를 기억에서 되살리지 않아도 됩니다. 로그는 공유되지 않고 내보내기에도 들어가지 않습니다."
        />
      ) : (
        <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <h2 className="text-card-title">기록</h2>
          <ul className="mt-1 flex flex-col divide-y divide-border-300">
            {items.map((log) => (
              <CareerLogRow
                key={log.id}
                log={log}
                entries={entryOptions}
                projects={projectOptions}
                onChanged={(next) =>
                  setItems((list) => list.map((i) => (i.id === next.id ? next : i)))
                }
                onDeleted={() => setItems((list) => list.filter((i) => i.id !== log.id))}
              />
            ))}
          </ul>
        </div>
      )}

      <LoadMore
        cursor={cursor}
        fetchPage={async (next) => {
          const { data, error } = await api.GET("/career-logs", {
            params: { query: { cursor: next, promoted, limit: PAGE_LIMIT } },
          });
          if (!data) throw new Error(error?.detail ?? "failed");
          return data;
        }}
        onLoaded={(more, next) => {
          setItems((list) => [...list, ...more]);
          setCursor(next);
        }}
      />
    </section>
  );
}
