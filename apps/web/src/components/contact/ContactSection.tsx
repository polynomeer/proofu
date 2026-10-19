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
import { CONTACT_RELATIONS, contactRelationLabel } from "@/lib/labels";
import { PAGE_LIMIT } from "@/lib/limits";

type Contact = Schema<"Contact">;
type Input = Schema<"ContactInput">;
type Relation = Schema<"ContactRelation">;
type CareerEntry = Schema<"CareerEntry">;
type Project = Schema<"Project">;

const EMPTY: Input = { displayName: "", relation: "PEER" };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
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

function ContactForm({
  initial,
  entries,
  projects,
  onSaved,
  onCancel,
}: {
  initial?: Contact;
  entries: { id: string; name: string }[];
  projects: { id: string; name: string }[];
  onSaved: (contact: Contact) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Input>(initial ? { ...initial } : EMPTY);
  const [careerEntryIds, setCareerEntryIds] = useState<string[]>(initial?.careerEntryIds ?? []);
  const [projectIds, setProjectIds] = useState<string[]>(initial?.projectIds ?? []);
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
      displayName: values.displayName.trim(),
      email: values.email?.trim() || null,
      organization: values.organization?.trim() || null,
      role: values.role?.trim() || null,
      note: values.note?.trim() || null,
      careerEntryIds,
      projectIds,
    };
    const { data, error } = initial
      ? await api.PATCH("/contacts/{id}", {
          params: { path: { id: initial.id } },
          body: { ...body, revision: initial.revision },
        })
      : await api.POST("/contacts", { body });
    setSubmitting(false);
    if (!data) {
      setFieldErrors(fieldErrorsOf(error));
      setProblem(
        error?.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요."
          : error?.code === "NOT_FOUND"
            ? "연결하려는 경력이나 프로젝트를 찾을 수 없습니다."
            : (error?.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    onSaved(data);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={`${prefix}-name`} label="이름" required error={fieldErrors.displayName}>
          <input
            id={`${prefix}-name`}
            className={inputClass}
            value={values.displayName}
            onChange={(e) => set("displayName", e.target.value)}
            aria-invalid={Boolean(fieldErrors.displayName)}
          />
        </Field>
        <Field id={`${prefix}-relation`} label="함께 일한 사이" required>
          <select
            id={`${prefix}-relation`}
            className={inputClass}
            value={values.relation}
            onChange={(e) => set("relation", e.target.value as Relation)}
          >
            {CONTACT_RELATIONS.map((r) => (
              <option key={r} value={r}>
                {contactRelationLabel(r)}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id={`${prefix}-email`}
          label="이메일"
          help="증언을 요청할 때만 씁니다. 없어도 기록할 수 있습니다."
          error={fieldErrors.email}
        >
          <input
            id={`${prefix}-email`}
            type="email"
            className={inputClass}
            value={values.email ?? ""}
            onChange={(e) => set("email", e.target.value || null)}
            aria-invalid={Boolean(fieldErrors.email)}
          />
        </Field>
        <Field id={`${prefix}-organization`} label="당시 소속" error={fieldErrors.organization}>
          <input
            id={`${prefix}-organization`}
            className={inputClass}
            value={values.organization ?? ""}
            onChange={(e) => set("organization", e.target.value || null)}
          />
        </Field>
        <Field id={`${prefix}-role`} label="당시 역할" error={fieldErrors.role}>
          <input
            id={`${prefix}-role`}
            className={inputClass}
            value={values.role ?? ""}
            onChange={(e) => set("role", e.target.value || null)}
          />
        </Field>
      </div>
      <Field
        id={`${prefix}-note`}
        label="메모"
        help="나만 봅니다. 모델에 보내지 않습니다."
        error={fieldErrors.note}
      >
        <textarea
          id={`${prefix}-note`}
          className={textareaClass}
          rows={2}
          value={values.note ?? ""}
          onChange={(e) => set("note", e.target.value || null)}
        />
      </Field>
      {entries.length > 0 || projects.length > 0 ? (
        <details className="rounded-md border border-border-300 p-3">
          <summary className="cursor-pointer text-caption font-semibold text-text-700">
            어디서 함께 일했는지 연결 (선택)
          </summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            <LinkPicker
              prefix={`${prefix}-entry`}
              label="경력"
              help="함께 일한 소속"
              options={entries}
              selected={careerEntryIds}
              onChange={setCareerEntryIds}
            />
            <LinkPicker
              prefix={`${prefix}-project`}
              label="프로젝트"
              help="함께한 프로젝트"
              options={projects}
              selected={projectIds}
              onChange={setProjectIds}
            />
          </div>
        </details>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "저장" : "인맥 추가"}
        </Button>
        <Button type="button" variant="tertiary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function ContactRow({
  contact,
  entries,
  projects,
  onChanged,
  onDeleted,
}: {
  contact: Contact;
  entries: { id: string; name: string }[];
  projects: { id: string; name: string }[];
  onChanged: (c: Contact) => void;
  onDeleted: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const linkNames = [
    ...(contact.careerEntryIds ?? []).map((id) => entries.find((e) => e.id === id)?.name),
    ...(contact.projectIds ?? []).map((id) => projects.find((p) => p.id === id)?.name),
  ].filter((name): name is string => Boolean(name));

  async function remove() {
    if (
      !window.confirm(
        `"${contact.displayName}"을(를) 삭제할까요?\n휴지통에 보관된 뒤 보존 기간이 지나면 영구 삭제됩니다.`,
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/contacts/{id}", { params: { path: { id: contact.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }

  if (editing) {
    return (
      <li className="py-3">
        <ContactForm
          initial={contact}
          entries={entries}
          projects={projects}
          onSaved={(next) => {
            onChanged(next);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-start gap-2 py-3">
      <div className="min-w-0 flex-1">
        <p className="text-body font-semibold">{contact.displayName}</p>
        <span className="mt-1 flex flex-wrap items-center gap-2 text-caption text-text-600">
          <Chip>{contactRelationLabel(contact.relation)}</Chip>
          {contact.organization ? <span>{contact.organization}</span> : null}
          {contact.role ? <span>{contact.role}</span> : null}
          {linkNames.length > 0 ? <span>{linkNames.join(" · ")}</span> : null}
          {!contact.reachable ? <span>이메일 없음 — 증언을 요청할 수 없습니다</span> : null}
        </span>
        {contact.note ? (
          <p className="mt-1 text-caption whitespace-pre-line text-text-600">{contact.note}</p>
        ) : null}
      </div>
      <Button variant="tertiary" className="h-8 px-2 text-caption" onClick={() => setEditing(true)}>
        편집
      </Button>
      <Button variant="tertiary" className="h-8 px-2 text-caption" onClick={remove} loading={busy}>
        삭제
      </Button>
    </li>
  );
}

/**
 * F14 인맥: the people the user worked with, kept so a claim can later be confirmed by one of
 * them (F15). Nobody is invited and nobody is notified — this is the user's own record of who
 * was there (docs/domain/career-data-model.md §인맥).
 */
export function ContactSection({
  initialItems,
  initialCursor,
  relation,
  entries,
  projects,
  pickersTruncated,
}: {
  initialItems: Contact[];
  initialCursor: string | null;
  relation?: Schema<"ContactRelation">;
  entries: CareerEntry[];
  projects: Project[];
  pickersTruncated: boolean;
}) {
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const [adding, setAdding] = useState(false);
  const entryOptions = entries.map((e) => ({ id: e.id, name: e.title }));
  const projectOptions = projects.map((p) => ({ id: p.id, name: p.name }));

  return (
    <section className="flex flex-col gap-4">
      {adding ? (
        <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <h2 className="text-card-title mb-4">새 인맥</h2>
          <ContactForm
            entries={entryOptions}
            projects={projectOptions}
            onSaved={(c) => {
              setItems((list) => [c, ...list]);
              setAdding(false);
            }}
            onCancel={() => setAdding(false)}
          />
          {pickersTruncated ? (
            <p className="mt-3 text-caption text-text-600">
              연결 목록에는 최근 경력·프로젝트만 보여 줍니다.
            </p>
          ) : null}
        </div>
      ) : (
        <Button variant="secondary" onClick={() => setAdding(true)} className="self-start">
          <Icon name="plus" size={16} />
          인맥 추가
        </Button>
      )}

      {items.length === 0 ? (
        <EmptyState
          title={relation ? "이 사이의 인맥이 없습니다" : "아직 기록한 인맥이 없습니다"}
          description="함께 일한 사람을 적어 두면, 나중에 어떤 주장을 누구에게 확인받을지 고를 수 있습니다. 상대에게는 아무것도 알리지 않으며, 기록은 모델에 보내지 않습니다."
        />
      ) : (
        <div className="rounded-md border border-border-300 bg-surface-000 p-4 md:p-6">
          <h2 className="text-card-title">인맥</h2>
          <ul className="mt-1 flex flex-col divide-y divide-border-300">
            {items.map((c) => (
              <ContactRow
                key={c.id}
                contact={c}
                entries={entryOptions}
                projects={projectOptions}
                onChanged={(next) =>
                  setItems((list) => list.map((i) => (i.id === next.id ? next : i)))
                }
                onDeleted={() => setItems((list) => list.filter((i) => i.id !== c.id))}
              />
            ))}
          </ul>
        </div>
      )}

      <LoadMore
        cursor={cursor}
        fetchPage={async (next) => {
          const { data, error } = await api.GET("/contacts", {
            params: { query: { cursor: next, relation, limit: PAGE_LIMIT } },
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
