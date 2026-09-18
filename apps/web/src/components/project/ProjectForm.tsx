"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button, ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { VISIBILITIES, visibilityLabel } from "@/lib/labels";

type Project = Schema<"Project">;
type Input = Schema<"ProjectInput">;
type Link = Schema<"ProjectLink">;

export type CareerEntryOption = { id: string; title: string; organization?: string };

const EMPTY: Input = { name: "", role: "", summary: "", links: [], visibility: "PRIVATE" };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

export function ProjectForm({
  project,
  careerEntries,
  defaultCareerEntryId,
}: {
  project?: Project;
  careerEntries: CareerEntryOption[];
  defaultCareerEntryId?: string;
}) {
  const router = useRouter();
  const [values, setValues] = useState<Input>(
    project ?? { ...EMPTY, careerEntryId: defaultCareerEntryId ?? null },
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const links = values.links ?? [];
  const setLink = (i: number, patch: Partial<Link>) =>
    set(
      "links",
      links.map((l, j) => (j === i ? { ...l, ...patch } : l)),
    );

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      careerEntryId: values.careerEntryId || null,
      startDate: values.startDate || undefined,
      endDate: values.endDate || undefined,
      teamSize: values.teamSize || undefined,
      links: links.filter((l) => l.label || l.url),
    };
    try {
      const result = project
        ? await api.PATCH("/projects/{id}", {
            params: { path: { id: project.id } },
            body: { ...body, revision: project.revision },
          })
        : await api.POST("/projects", { body });
      if (result.error) {
        const errors = fieldErrorsOf(result.error);
        setFieldErrors(errors);
        setProblem(
          result.error.code === "CONFLICT_STALE_VERSION"
            ? "다른 곳에서 먼저 수정되었습니다. 페이지를 새로고침한 뒤 다시 편집하세요."
            : Object.keys(errors).length > 0
              ? "표시된 항목을 확인하세요."
              : (result.error.detail ?? "저장하지 못했습니다."),
        );
        return;
      }
      router.push(`/projects/${result.data.id}`);
      router.refresh();
    } catch {
      setProblem("API 서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-6">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}

      <fieldset className="flex flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-4">
        <legend className="px-1 text-card-title">기본 정보</legend>
        <Field id="name" label="프로젝트명" required error={fieldErrors.name}>
          <input
            id="name"
            className={inputClass}
            value={values.name}
            maxLength={200}
            aria-invalid={fieldErrors.name ? true : undefined}
            onChange={(e) => set("name", e.target.value)}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="role" label="역할" required help="예: 테크 리드, PM" error={fieldErrors.role}>
            <input
              id="role"
              className={inputClass}
              value={values.role}
              maxLength={200}
              aria-invalid={fieldErrors.role ? true : undefined}
              onChange={(e) => set("role", e.target.value)}
            />
          </Field>
          <Field id="careerEntryId" label="연결된 경력" help="없으면 독립 프로젝트">
            <select
              id="careerEntryId"
              className={inputClass}
              value={values.careerEntryId ?? ""}
              onChange={(e) => set("careerEntryId", e.target.value || null)}
            >
              <option value="">연결 안 함</option>
              {careerEntries.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                  {c.organization ? ` · ${c.organization}` : ""}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field
          id="summary"
          label="요약"
          required
          help="목표, 활동, 결과를 사실 중심으로"
          error={fieldErrors.summary}
        >
          <textarea
            id="summary"
            className={textareaClass}
            value={values.summary}
            aria-invalid={fieldErrors.summary ? true : undefined}
            onChange={(e) => set("summary", e.target.value)}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field id="startDate" label="시작일" error={fieldErrors.startDate}>
            <input
              id="startDate"
              type="date"
              className={inputClass}
              value={values.startDate ?? ""}
              onChange={(e) => set("startDate", e.target.value)}
            />
          </Field>
          <Field id="endDate" label="종료일" help="진행 중이면 비움" error={fieldErrors.endDate}>
            <input
              id="endDate"
              type="date"
              className={inputClass}
              value={values.endDate ?? ""}
              onChange={(e) => set("endDate", e.target.value)}
            />
          </Field>
          <Field id="teamSize" label="팀 규모" error={fieldErrors.teamSize}>
            <input
              id="teamSize"
              type="number"
              min={1}
              className={inputClass}
              value={values.teamSize ?? ""}
              onChange={(e) => set("teamSize", e.target.value ? Number(e.target.value) : undefined)}
            />
          </Field>
        </div>
        <Field id="visibility" label="공개 범위" error={fieldErrors.visibility}>
          <select
            id="visibility"
            className={inputClass}
            value={values.visibility ?? "PRIVATE"}
            onChange={(e) => set("visibility", e.target.value as Input["visibility"])}
          >
            {VISIBILITIES.map((v) => (
              <option key={v} value={v}>
                {visibilityLabel(v)}
              </option>
            ))}
          </select>
        </Field>
      </fieldset>

      <fieldset className="flex flex-col gap-3 rounded-md border border-border-300 bg-surface-000 p-4">
        <legend className="px-1 text-card-title">링크</legend>
        <p className="text-caption text-text-600">
          저장소, 데모, 발표 자료 등 외부 URL. 링크 자체는 Evidence가 아니며 근거는 별도로
          연결합니다.
        </p>
        {links.map((link, i) => (
          <div key={i} className="grid grid-cols-[1fr_2fr_auto] gap-2">
            <input
              aria-label={`링크 ${i + 1} 이름`}
              className={inputClass}
              placeholder="이름"
              maxLength={80}
              value={link.label}
              onChange={(e) => setLink(i, { label: e.target.value })}
            />
            <input
              aria-label={`링크 ${i + 1} URL`}
              className={inputClass}
              placeholder="https://"
              type="url"
              value={link.url}
              onChange={(e) => setLink(i, { url: e.target.value })}
            />
            <Button
              type="button"
              variant="tertiary"
              aria-label={`링크 ${i + 1} 제거`}
              onClick={() =>
                set(
                  "links",
                  links.filter((_, j) => j !== i),
                )
              }
            >
              제거
            </Button>
          </div>
        ))}
        {fieldErrors.links ? (
          <p role="alert" className="text-caption text-warning-700">
            {fieldErrors.links}
          </p>
        ) : null}
        <div>
          <Button
            type="button"
            variant="secondary"
            disabled={links.length >= 20}
            onClick={() => set("links", [...links, { label: "", url: "" }])}
          >
            <Icon name="plus" size={16} />
            링크 추가
          </Button>
        </div>
      </fieldset>

      <div className="flex gap-3">
        <Button type="submit" loading={submitting}>
          {project ? "변경 사항 저장" : "프로젝트 저장"}
        </Button>
        <ButtonLink
          variant="secondary"
          href={
            project
              ? `/projects/${project.id}`
              : defaultCareerEntryId
                ? `/career/${defaultCareerEntryId}`
                : "/career"
          }
        >
          취소
        </ButtonLink>
      </div>
    </form>
  );
}
