"use client";

import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { LoadMore } from "@/components/ui/LoadMore";
import { api } from "@/lib/api";
import { PAGE_LIMIT } from "@/lib/limits";
import { formatDate } from "@/lib/format";
import {
  PROFICIENCY_LEVELS,
  SKILL_CATEGORIES,
  proficiencyLabel,
  skillCategoryLabel,
} from "@/lib/labels";

type Skill = Schema<"Skill">;
type Input = Schema<"SkillInput">;
type Category = Schema<"SkillCategory">;

const EMPTY: Input = { canonicalName: "", category: "PROGRAMMING_LANGUAGE", aliases: [] };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

function SkillForm({
  initial,
  onSaved,
  onCancel,
}: {
  initial?: Skill;
  onSaved: (s: Skill) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Input>(initial ? { ...initial } : EMPTY);
  const [aliasText, setAliasText] = useState((initial?.aliases ?? []).join(", "));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const prefix = initial ? `s-${initial.id}` : "s-new";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      canonicalName: values.canonicalName.trim(),
      aliases: aliasText
        .split(",")
        .map((a) => a.trim())
        .filter(Boolean),
      proficiency: values.proficiency ?? null,
      lastUsedAt: values.lastUsedAt || null,
    };
    const { data, error } = initial
      ? await api.PATCH("/skills/{id}", {
          params: { path: { id: initial.id } },
          body: { ...body, revision: initial.revision },
        })
      : await api.POST("/skills", { body });
    setSubmitting(false);
    if (!data) {
      setFieldErrors(fieldErrorsOf(error));
      setProblem(
        error?.code === "CONFLICT_DUPLICATE"
          ? `이미 같은 기술이 있습니다: ${error.detail ?? ""}`
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
        <Field
          id={`${prefix}-name`}
          label="이름"
          required
          error={fieldErrors.canonicalName}
          help="대표로 쓸 이름"
        >
          <input
            id={`${prefix}-name`}
            className={inputClass}
            value={values.canonicalName}
            onChange={(e) => set("canonicalName", e.target.value)}
            aria-invalid={Boolean(fieldErrors.canonicalName)}
          />
        </Field>
        <Field id={`${prefix}-category`} label="분류" required>
          <select
            id={`${prefix}-category`}
            className={inputClass}
            value={values.category}
            onChange={(e) => set("category", e.target.value as Category)}
          >
            {SKILL_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {skillCategoryLabel(c)}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id={`${prefix}-aliases`}
          label="다른 이름"
          help="쉼표로 구분. 공고마다 다르게 쓰는 표기"
          error={fieldErrors.aliases}
        >
          <input
            id={`${prefix}-aliases`}
            className={inputClass}
            value={aliasText}
            onChange={(e) => setAliasText(e.target.value)}
            placeholder="SpringBoot, 스프링 부트"
          />
        </Field>
        <Field
          id={`${prefix}-proficiency`}
          label="숙련도 (자기평가)"
          help="Evidence가 올려 주지 않습니다"
        >
          <select
            id={`${prefix}-proficiency`}
            className={inputClass}
            value={values.proficiency ?? ""}
            onChange={(e) => set("proficiency", (e.target.value || null) as Input["proficiency"])}
          >
            <option value="">선택 안 함</option>
            {PROFICIENCY_LEVELS.map((p) => (
              <option key={p} value={p}>
                {proficiencyLabel(p)}
              </option>
            ))}
          </select>
        </Field>
        <Field id={`${prefix}-last-used`} label="마지막 사용" error={fieldErrors.lastUsedAt}>
          <input
            id={`${prefix}-last-used`}
            type="date"
            className={inputClass}
            value={values.lastUsedAt ?? ""}
            onChange={(e) => set("lastUsedAt", e.target.value || null)}
          />
        </Field>
      </div>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "저장" : "기술 추가"}
        </Button>
        <Button type="button" variant="tertiary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function SkillRow({
  skill,
  onChanged,
  onDeleted,
}: {
  skill: Skill;
  onChanged: (s: Skill) => void;
  onDeleted: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  async function remove() {
    if (
      !window.confirm(
        `"${skill.canonicalName}" 기술을 삭제할까요?\n프로젝트 연결이 바로 끊어지고, 휴지통에 보관된 뒤 설정한 보존 기간이 지나면 영구 삭제됩니다.`,
      )
    )
      return;
    setBusy(true);
    const { error } = await api.DELETE("/skills/{id}", { params: { path: { id: skill.id } } });
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
        <SkillForm
          initial={skill}
          onSaved={(s) => {
            onChanged(s);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-2 py-3">
      <span className="min-w-0 flex-1">
        <span className="text-body font-semibold">{skill.canonicalName}</span>
        {skill.aliases.length > 0 ? (
          <span className="ml-2 text-caption text-text-600">{skill.aliases.join(", ")}</span>
        ) : null}
        <span className="mt-1 flex flex-wrap items-center gap-2 text-caption text-text-600">
          <Chip>{skillCategoryLabel(skill.category)}</Chip>
          {skill.proficiency ? <span>{proficiencyLabel(skill.proficiency)}</span> : null}
          {skill.lastUsedAt ? <span>마지막 사용 {formatDate(skill.lastUsedAt)}</span> : null}
        </span>
      </span>
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
 * F01 기술: one record per name in the workspace. The list is the source for the skills a
 * project claims; matching does not score skills (docs/domain/career-data-model.md §기술).
 */
export function SkillSection({
  initialItems,
  initialCursor,
}: {
  initialItems: Skill[];
  initialCursor: string | null;
}) {
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const [adding, setAdding] = useState(false);

  const byCategory = SKILL_CATEGORIES.map((c) => ({
    category: c,
    skills: items.filter((s) => s.category === c),
  })).filter((g) => g.skills.length > 0);

  return (
    <section className="flex flex-col gap-4">
      {adding ? (
        <div className="rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-card-title mb-4">새 기술</h2>
          <SkillForm
            onSaved={(s) => {
              setItems((list) => [s, ...list]);
              setAdding(false);
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      ) : (
        <Button variant="secondary" onClick={() => setAdding(true)} className="self-start">
          <Icon name="plus" size={16} />
          기술 추가
        </Button>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="아직 등록된 기술이 없습니다"
          description="자주 쓰는 언어·프레임워크·도구를 등록해 두면 프로젝트마다 다시 적지 않아도 됩니다. 공고에서 다르게 부르는 표기는 '다른 이름'에 모아 두세요."
        />
      ) : (
        byCategory.map((group) => (
          <div
            key={group.category}
            className="rounded-md border border-border-300 bg-surface-000 p-6"
          >
            <h2 className="text-card-title">{skillCategoryLabel(group.category)}</h2>
            <ul className="mt-1 flex flex-col divide-y divide-border-300">
              {group.skills.map((s) => (
                <SkillRow
                  key={s.id}
                  skill={s}
                  onChanged={(next) =>
                    setItems((list) => list.map((i) => (i.id === next.id ? next : i)))
                  }
                  onDeleted={() => setItems((list) => list.filter((i) => i.id !== s.id))}
                />
              ))}
            </ul>
          </div>
        ))
      )}

      <LoadMore
        cursor={cursor}
        fetchPage={async (next) => {
          const { data, error } = await api.GET("/skills", {
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
