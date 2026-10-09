"use client";

import { useMemo, useRef, useState, type FormEvent, type MouseEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import {
  REQUIREMENT_CATEGORIES,
  requirementCategoryLabel,
  requirementStatusLabel,
} from "@/lib/labels";

type Requirement = Schema<"Requirement">;
type Category = Schema<"RequirementCategory">;
type Span = Schema<"SourceSpan">;

type Draft = { category: Category; text: string; span: Span | null };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

/** Character offset of a DOM point measured against the container's full text. */
function offsetWithin(container: Node, node: Node, offset: number): number {
  const range = document.createRange();
  range.setStart(container, 0);
  range.setEnd(node, offset);
  return range.toString().length;
}

function StatusChip({ status }: { status: Requirement["status"] }) {
  const tone = status === "APPROVED" ? "verified" : status === "REJECTED" ? "expired" : "review";
  return <Chip tone={tone}>{requirementStatusLabel(status)}</Chip>;
}

function RequirementForm({
  snapshotId,
  draft,
  initial,
  onSaved,
  onCancel,
}: {
  snapshotId: string;
  draft: Draft;
  initial?: Requirement;
  onSaved: (r: Requirement) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Draft>(draft);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const prefix = initial ? `req-${initial.id}` : "req-new";

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const result = initial
      ? await api.PATCH("/requirements/{id}", {
          params: { path: { id: initial.id } },
          body: {
            version: initial.version,
            category: values.category,
            text: values.text,
            sourceSpan: values.span ?? undefined,
          },
        })
      : await api.POST("/job-posting-snapshots/{id}/requirements", {
          params: { path: { id: snapshotId } },
          body: { category: values.category, text: values.text, sourceSpan: values.span },
        });
    setSubmitting(false);
    if (result.error) {
      const errors = fieldErrorsOf(result.error);
      setFieldErrors(errors);
      setProblem(
        result.error.code === "DOMAIN_RULE_VIOLATION"
          ? "원문 구간이 본문 밖을 가리키거나 내용이 비어 있습니다."
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
      className="flex flex-col gap-3 rounded-md border border-primary-600/40 bg-surface-050 p-3"
    >
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <div className="grid gap-3 sm:grid-cols-[180px_1fr]">
        <Field id={`${prefix}-category`} label="분류" required>
          <select
            id={`${prefix}-category`}
            className={inputClass}
            value={values.category}
            onChange={(e) => setValues((v) => ({ ...v, category: e.target.value as Category }))}
          >
            {REQUIREMENT_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {requirementCategoryLabel(c)}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id={`${prefix}-text`}
          label="요구사항"
          required
          help="공고가 요구하는 내용을 한 문장으로"
          error={fieldErrors.text}
        >
          <textarea
            id={`${prefix}-text`}
            className={`${textareaClass} min-h-20`}
            value={values.text}
            aria-invalid={fieldErrors.text ? true : undefined}
            onChange={(e) => setValues((v) => ({ ...v, text: e.target.value }))}
          />
        </Field>
      </div>
      <p className="text-caption text-text-600">
        {values.span ? (
          <>
            원문 구간 {values.span.start}–{values.span.end}
            <button
              type="button"
              className="ml-2 text-primary-600 hover:underline"
              onClick={() => setValues((v) => ({ ...v, span: null }))}
            >
              구간 해제
            </button>
          </>
        ) : (
          "원문을 드래그해서 추가하면 구간이 함께 저장됩니다."
        )}
      </p>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "변경 사항 저장" : "요구사항 추가"}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function RequirementRow({
  item,
  onChange,
  onEdit,
  onDeleted,
}: {
  item: Requirement;
  onChange: (r: Requirement) => void;
  onEdit: () => void;
  onDeleted: () => void;
}) {
  const [busy, setBusy] = useState(false);

  async function setStatus(status: "APPROVED" | "REJECTED") {
    setBusy(true);
    const result = await api.PATCH("/requirements/{id}", {
      params: { path: { id: item.id } },
      body: { version: item.version, status },
    });
    setBusy(false);
    if (result.error) {
      window.alert(result.error.detail ?? "상태를 바꾸지 못했습니다.");
      return;
    }
    onChange(result.data);
  }

  async function remove() {
    if (!window.confirm(`"${item.text}" 요구사항을 삭제할까요?`)) return;
    setBusy(true);
    const { error } = await api.DELETE("/requirements/{id}", { params: { path: { id: item.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }

  return (
    <li
      className={`flex flex-col gap-1 px-3 py-3 ${item.status === "REJECTED" ? "opacity-60" : ""}`}
    >
      <div className="flex flex-wrap items-start gap-2">
        <p className="min-w-0 flex-1 text-body">{item.text}</p>
        <div className="flex shrink-0 gap-1">
          {item.status !== "APPROVED" ? (
            <Button
              type="button"
              variant="tertiary"
              onClick={() => setStatus("APPROVED")}
              loading={busy}
            >
              승인
            </Button>
          ) : null}
          {item.status !== "REJECTED" ? (
            <Button
              type="button"
              variant="tertiary"
              onClick={() => setStatus("REJECTED")}
              loading={busy}
            >
              제외
            </Button>
          ) : null}
          <Button type="button" variant="tertiary" onClick={onEdit} disabled={busy}>
            편집
          </Button>
          <Button type="button" variant="tertiary" onClick={remove} disabled={busy}>
            삭제
          </Button>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-caption text-text-600">
        <StatusChip status={item.status} />
        <Chip>{item.origin === "AI" ? "AI 추출" : "직접 입력"}</Chip>
        {item.excerpt ? <span className="truncate">원문: “{item.excerpt}”</span> : null}
      </div>
    </li>
  );
}

/**
 * J02 requirement review for one snapshot. Selecting text in the raw posting offers to add it
 * as a requirement with its source span; approved spans are highlighted in the text.
 */
export function SnapshotRequirementsPanel({
  snapshotId,
  rawText,
  initialItems,
}: {
  snapshotId: string;
  rawText: string;
  initialItems: Requirement[];
}) {
  const textRef = useRef<HTMLPreElement>(null);
  const [items, setItems] = useState(initialItems);
  const [selection, setSelection] = useState<{
    span: Span;
    text: string;
    x: number;
    y: number;
  } | null>(null);
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const [draft, setDraft] = useState<Draft>({ category: "REQUIRED", text: "", span: null });

  function onMouseUp(e: MouseEvent<HTMLPreElement>) {
    const container = textRef.current;
    const sel = window.getSelection();
    if (!container || !sel || sel.isCollapsed || sel.rangeCount === 0) {
      setSelection(null);
      return;
    }
    const range = sel.getRangeAt(0);
    if (!container.contains(range.startContainer) || !container.contains(range.endContainer))
      return;
    const start = offsetWithin(container, range.startContainer, range.startOffset);
    const end = offsetWithin(container, range.endContainer, range.endOffset);
    if (end <= start) return;
    const rect = container.getBoundingClientRect();
    setSelection({
      span: { start, end },
      text: rawText.slice(start, end).trim(),
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  }

  function addFromSelection() {
    if (!selection) return;
    setDraft({ category: "REQUIRED", text: selection.text, span: selection.span });
    setEditing("new");
    setSelection(null);
    window.getSelection()?.removeAllRanges();
  }

  // Non-overlapping approved spans → text segments with <mark>.
  const segments = useMemo(() => {
    const spans = items
      .filter((r) => r.status === "APPROVED" && r.sourceSpan)
      .map((r) => r.sourceSpan as Span)
      .sort((a, b) => a.start - b.start);
    const out: { text: string; marked: boolean }[] = [];
    let cursor = 0;
    for (const s of spans) {
      if (s.start < cursor || s.end > rawText.length) continue;
      if (s.start > cursor) out.push({ text: rawText.slice(cursor, s.start), marked: false });
      out.push({ text: rawText.slice(s.start, s.end), marked: true });
      cursor = s.end;
    }
    if (cursor < rawText.length) out.push({ text: rawText.slice(cursor), marked: false });
    return out;
  }, [items, rawText]);

  const grouped = REQUIREMENT_CATEGORIES.map((c) => ({
    category: c,
    items: items.filter((r) => r.category === c),
  })).filter((g) => g.items.length > 0);
  const approved = items.filter((r) => r.status === "APPROVED").length;
  const drafts = items.filter((r) => r.status === "DRAFT").length;

  return (
    <div className="flex flex-col gap-6">
      <section className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-section-title">공고 원문</h2>
          <span className="text-caption text-text-600">
            본문을 드래그하면 요구사항으로 추가할 수 있습니다
          </span>
        </div>
        <div className="relative">
          <pre
            ref={textRef}
            onMouseUp={onMouseUp}
            className="max-h-[60vh] overflow-auto rounded-md bg-surface-050 p-4 font-sans text-body whitespace-pre-wrap select-text"
          >
            {segments.map((s, i) =>
              s.marked ? (
                <mark key={i} className="rounded-sm bg-primary-050 text-text-900">
                  {s.text}
                </mark>
              ) : (
                <span key={i}>{s.text}</span>
              ),
            )}
          </pre>
          {selection ? (
            <div
              className="absolute z-10"
              style={{ left: Math.max(0, selection.x - 60), top: selection.y + 8 }}
            >
              <Button type="button" onClick={addFromSelection}>
                <Icon name="plus" size={16} />
                요구사항으로 추가
              </Button>
            </div>
          ) : null}
        </div>
      </section>

      <section className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
        <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-section-title">
            요구사항{" "}
            <span className="text-body font-normal text-text-600">
              승인 {approved} / 전체 {items.length}
              {drafts > 0 ? ` · 검토 필요 ${drafts}` : ""}
            </span>
          </h2>
          {editing === null ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setDraft({ category: "REQUIRED", text: "", span: null });
                setEditing("new");
              }}
            >
              <Icon name="plus" size={16} />
              직접 추가
            </Button>
          ) : null}
        </div>
        <p className="mb-4 text-caption text-text-600">
          승인된 항목만 경력 매칭과 문서 생성에 쓰입니다. AI가 뽑은 초안은 검토 필요 상태이며,
          승인하기 전에는 어디에도 쓰이지 않습니다.
        </p>

        {editing === "new" ? (
          <div className="mb-4">
            <RequirementForm
              snapshotId={snapshotId}
              draft={draft}
              onSaved={(r) => {
                setItems((list) => [...list, r]);
                setEditing(null);
              }}
              onCancel={() => setEditing(null)}
            />
          </div>
        ) : null}

        {items.length === 0 && editing !== "new" ? (
          <EmptyState
            title="아직 요구사항이 없습니다"
            description="원문에서 필수 조건, 우대 조건, 책임, 기술을 한 문장씩 뽑아 두면 매칭의 기준이 됩니다."
          />
        ) : (
          <div className="flex flex-col gap-4">
            {grouped.map((g) => (
              <div key={g.category}>
                <h3 className="mb-1 text-caption font-semibold text-text-600">
                  {requirementCategoryLabel(g.category)} ({g.items.length})
                </h3>
                <ul className="divide-y divide-border-300 rounded-md border border-border-300">
                  {g.items.map((r) =>
                    editing === r.id ? (
                      <li key={r.id} className="p-2">
                        <RequirementForm
                          snapshotId={snapshotId}
                          draft={{ category: r.category, text: r.text, span: r.sourceSpan ?? null }}
                          initial={r}
                          onSaved={(saved) => {
                            setItems((list) => list.map((x) => (x.id === saved.id ? saved : x)));
                            setEditing(null);
                          }}
                          onCancel={() => setEditing(null)}
                        />
                      </li>
                    ) : (
                      <RequirementRow
                        key={r.id}
                        item={r}
                        onChange={(next) =>
                          setItems((list) => list.map((x) => (x.id === next.id ? next : x)))
                        }
                        onEdit={() => setEditing(r.id)}
                        onDeleted={() => setItems((list) => list.filter((x) => x.id !== r.id))}
                      />
                    ),
                  )}
                </ul>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
