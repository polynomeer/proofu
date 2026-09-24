"use client";

import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { LoadMore } from "@/components/ui/LoadMore";
import { api } from "@/lib/api";
import { PAGE_LIMIT } from "@/lib/limits";
import {
  CAPABILITY_CATEGORIES,
  PROFICIENCY_LEVELS,
  capabilityCategoryLabel,
  capabilityEvidenceStatusLabel,
  proficiencyLabel,
  verificationLabel,
} from "@/lib/labels";

type Capability = Schema<"Capability">;
type Input = Schema<"CapabilityInput">;
type Category = Schema<"CapabilityCategory">;
type Evidence = Schema<"Evidence">;

const EMPTY: Input = { name: "", definition: "", category: "TECHNICAL" };

function statusTone(status: Capability["evidenceStatus"]) {
  return status === "VERIFIED" ? "verified" : status === "UNVERIFIED" ? "review" : "neutral";
}

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

function CapabilityForm({
  initial,
  parents,
  onSaved,
  onCancel,
}: {
  initial?: Capability;
  parents: Capability[];
  onSaved: (c: Capability) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Input>(initial ? { ...initial } : EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const prefix = initial ? `c-${initial.id}` : "c-new";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      name: values.name.trim(),
      definition: values.definition.trim(),
      evidenceCriteria: values.evidenceCriteria?.trim() || null,
      selfAssessedLevel: values.selfAssessedLevel ?? null,
      parentId: values.parentId ?? null,
    };
    const { data, error } = initial
      ? await api.PATCH("/capabilities/{id}", {
          params: { path: { id: initial.id } },
          body: { ...body, revision: initial.revision },
        })
      : await api.POST("/capabilities", { body });
    setSubmitting(false);
    if (!data) {
      setFieldErrors(fieldErrorsOf(error));
      setProblem(error?.detail ?? "저장하지 못했습니다.");
      return;
    }
    onSaved(data);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={`${prefix}-name`} label="역량 이름" required error={fieldErrors.name}>
          <input
            id={`${prefix}-name`}
            className={inputClass}
            value={values.name}
            onChange={(e) => set("name", e.target.value)}
            aria-invalid={Boolean(fieldErrors.name)}
          />
        </Field>
        <Field id={`${prefix}-category`} label="분류" required>
          <select
            id={`${prefix}-category`}
            className={inputClass}
            value={values.category}
            onChange={(e) => set("category", e.target.value as Category)}
          >
            {CAPABILITY_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {capabilityCategoryLabel(c)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field
        id={`${prefix}-definition`}
        label="정의"
        required
        help="무엇을 할 수 있는지 행동으로 씁니다"
        error={fieldErrors.definition}
      >
        <textarea
          id={`${prefix}-definition`}
          className={textareaClass}
          value={values.definition}
          onChange={(e) => set("definition", e.target.value)}
          aria-invalid={Boolean(fieldErrors.definition)}
        />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={`${prefix}-parent`} label="상위 역량" help="없으면 최상위">
          <select
            id={`${prefix}-parent`}
            className={inputClass}
            value={values.parentId ?? ""}
            onChange={(e) => set("parentId", e.target.value || null)}
          >
            <option value="">없음</option>
            {parents
              .filter((p) => p.id !== initial?.id)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
          </select>
        </Field>
        <Field id={`${prefix}-level`} label="수준 (자기평가)" help="Evidence가 올려 주지 않습니다">
          <select
            id={`${prefix}-level`}
            className={inputClass}
            value={values.selfAssessedLevel ?? ""}
            onChange={(e) =>
              set("selfAssessedLevel", (e.target.value || null) as Input["selfAssessedLevel"])
            }
          >
            <option value="">선택 안 함</option>
            {PROFICIENCY_LEVELS.map((p) => (
              <option key={p} value={p}>
                {proficiencyLabel(p)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field
        id={`${prefix}-criteria`}
        label="근거 기준"
        help="이 역량을 주장하려면 어떤 Evidence가 필요한지 스스로 정합니다"
      >
        <input
          id={`${prefix}-criteria`}
          className={inputClass}
          value={values.evidenceCriteria ?? ""}
          onChange={(e) => set("evidenceCriteria", e.target.value)}
          placeholder="장애 회고 2건 이상, 설계 문서 1건"
        />
      </Field>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "저장" : "역량 추가"}
        </Button>
        <Button type="button" variant="tertiary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function EvidencePicker({
  capability,
  evidence,
  capped,
  onSaved,
  onCancel,
}: {
  capability: Capability;
  evidence: Evidence[];
  capped: boolean;
  onSaved: (c: Capability) => void;
  onCancel: () => void;
}) {
  const [selected, setSelected] = useState(() => new Set(capability.evidence.map((e) => e.id)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    const { data, error: problem } = await api.PUT("/capabilities/{id}/evidence", {
      params: { path: { id: capability.id } },
      body: { evidenceIds: [...selected] },
    });
    setBusy(false);
    if (!data) {
      setError(problem?.detail ?? "저장하지 못했습니다.");
      return;
    }
    onSaved(data);
  }

  if (evidence.length === 0) {
    return (
      <p className="text-caption text-text-600">
        등록된 Evidence가 없습니다. 먼저 Evidence를 추가하세요.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {capped ? (
        <p className="text-caption text-text-600">
          최근 Evidence {evidence.length}건만 보여 줍니다. 찾는 항목이 없으면 Evidence 화면에서
          연결하세요.
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-caption text-warning-700">
          {error}
        </p>
      ) : null}
      <ul className="flex flex-col gap-1">
        {evidence.map((e) => (
          <li key={e.id}>
            <label
              htmlFor={`ce-${capability.id}-${e.id}`}
              className="flex items-center gap-2 text-body"
            >
              <input
                id={`ce-${capability.id}-${e.id}`}
                type="checkbox"
                aria-label={e.title}
                checked={selected.has(e.id)}
                onChange={(event) =>
                  setSelected((prev) => {
                    const next = new Set(prev);
                    if (event.target.checked) next.add(e.id);
                    else next.delete(e.id);
                    return next;
                  })
                }
              />
              <span>{e.title}</span>
              <span className="text-caption text-text-600">
                {verificationLabel(e.verification)}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <Button onClick={save} loading={busy}>
          저장
        </Button>
        <Button variant="tertiary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </div>
  );
}

function CapabilityCard({
  capability,
  nested,
  parents,
  evidence,
  evidenceCapped,
  onChanged,
  onDeleted,
}: {
  capability: Capability;
  nested: Capability[];
  parents: Capability[];
  evidence: Evidence[];
  evidenceCapped: boolean;
  onChanged: (c: Capability) => void;
  onDeleted: (ids: string[]) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [linking, setLinking] = useState(false);
  const [busy, setBusy] = useState(false);

  async function remove() {
    const note =
      nested.length > 0 ? `\n하위 역량 ${nested.length}개도 함께 휴지통으로 이동합니다.` : "";
    if (
      !window.confirm(
        `"${capability.name}" 역량을 삭제할까요?${note}\n휴지통에 보관된 뒤 설정한 보존 기간이 지나면 영구 삭제됩니다.`,
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/capabilities/{id}", {
      params: { path: { id: capability.id } },
    });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted([capability.id, ...nested.map((c) => c.id)]);
  }

  return (
    <li className="py-4">
      {editing ? (
        <CapabilityForm
          initial={capability}
          parents={parents}
          onSaved={(c) => {
            onChanged(c);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-body font-semibold">{capability.name}</span>
            <Chip tone={statusTone(capability.evidenceStatus)}>
              {capabilityEvidenceStatusLabel(capability.evidenceStatus)}
            </Chip>
            {capability.selfAssessedLevel ? (
              <span className="text-caption text-text-600">
                자기평가 {proficiencyLabel(capability.selfAssessedLevel)}
              </span>
            ) : null}
            <span className="flex-1" />
            <Button
              variant="tertiary"
              className="h-8 px-2 text-caption"
              onClick={() => setLinking((v) => !v)}
            >
              Evidence 연결
            </Button>
            <Button
              variant="tertiary"
              className="h-8 px-2 text-caption"
              onClick={() => setEditing(true)}
            >
              편집
            </Button>
            <Button
              variant="tertiary"
              className="h-8 px-2 text-caption"
              onClick={remove}
              loading={busy}
            >
              삭제
            </Button>
          </div>
          <p className="text-body text-text-600">{capability.definition}</p>
          {capability.evidenceCriteria ? (
            <p className="text-caption text-text-600">근거 기준: {capability.evidenceCriteria}</p>
          ) : null}
          {capability.evidence.length > 0 ? (
            <ul className="flex flex-wrap gap-2">
              {capability.evidence.map((e) => (
                <li key={e.id}>
                  <Chip tone={e.verification === "UNVERIFIED" ? "neutral" : "verified"}>
                    {e.title}
                  </Chip>
                </li>
              ))}
            </ul>
          ) : null}
          {linking ? (
            <EvidencePicker
              capability={capability}
              evidence={evidence}
              capped={evidenceCapped}
              onSaved={(c) => {
                onChanged(c);
                setLinking(false);
              }}
              onCancel={() => setLinking(false)}
            />
          ) : null}
          {nested.length > 0 ? (
            <ul className="ml-4 border-l border-border-300 pl-4 text-caption text-text-600">
              {nested.map((c) => (
                <li key={c.id}>
                  {c.name} · {capabilityEvidenceStatusLabel(c.evidenceStatus)}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      )}
    </li>
  );
}

/**
 * E03 역량: definitions the user owns, grouped by category, with the evidence attached to each.
 * The level is self-assessed; the chip reports what evidence is linked, not a judgement.
 */
export function CapabilitySection({
  initialItems,
  initialCursor,
  evidence,
  evidenceCapped,
}: {
  initialItems: Capability[];
  initialCursor: string | null;
  evidence: Evidence[];
  evidenceCapped: boolean;
}) {
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const [adding, setAdding] = useState(false);

  const byCategory = CAPABILITY_CATEGORIES.map((category) => ({
    category,
    roots: items.filter((c) => c.category === category && !c.parentId),
  })).filter((g) => g.roots.length > 0);
  const orphans = items.filter((c) => c.parentId && !items.some((p) => p.id === c.parentId));

  return (
    <section className="flex flex-col gap-4">
      {adding ? (
        <div className="rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-card-title mb-4">새 역량</h2>
          <CapabilityForm
            parents={items}
            onSaved={(c) => {
              setItems((list) => [c, ...list]);
              setAdding(false);
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      ) : (
        <Button variant="secondary" onClick={() => setAdding(true)} className="self-start">
          <Icon name="plus" size={16} />
          역량 추가
        </Button>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="아직 정의한 역량이 없습니다"
          description="이름만으로는 의미가 흔들립니다. '무엇을 할 수 있는가'를 한 문장으로 정의하고, 그것을 뒷받침하는 Evidence를 연결하세요."
        />
      ) : (
        [
          ...byCategory,
          ...(orphans.length > 0 ? [{ category: "OTHER_PARENT" as const, roots: orphans }] : []),
        ].map((group) => (
          <div
            key={group.category}
            className="rounded-md border border-border-300 bg-surface-000 p-6"
          >
            <h2 className="text-card-title">
              {group.category === "OTHER_PARENT"
                ? "상위 역량을 찾을 수 없음"
                : capabilityCategoryLabel(group.category)}
            </h2>
            <ul className="flex flex-col divide-y divide-border-300">
              {group.roots.map((c) => (
                <CapabilityCard
                  key={c.id}
                  capability={c}
                  nested={items.filter((child) => child.parentId === c.id)}
                  parents={items}
                  evidence={evidence}
                  evidenceCapped={evidenceCapped}
                  onChanged={(next) =>
                    setItems((list) => list.map((i) => (i.id === next.id ? next : i)))
                  }
                  onDeleted={(ids) => setItems((list) => list.filter((i) => !ids.includes(i.id)))}
                />
              ))}
            </ul>
          </div>
        ))
      )}

      <LoadMore
        cursor={cursor}
        fetchPage={async (next) => {
          const { data, error } = await api.GET("/capabilities", {
            params: { query: { cursor: next, limit: PAGE_LIMIT } },
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
