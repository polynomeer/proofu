"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button, ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import {
  CAREER_ENTRY_TYPES,
  VISIBILITIES,
  careerEntryTypeLabel,
  visibilityLabel,
} from "@/lib/labels";

type Entry = Schema<"CareerEntry">;
type Input = Schema<"CareerEntryInput">;

const EMPTY: Input = { type: "EMPLOYMENT", title: "", startDate: "", visibility: "PRIVATE" };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

/** Create (no `entry`) or edit (with `entry`, sending its revision for optimistic locking). */
export function CareerEntryForm({
  entry,
  defaultVisibility = "PRIVATE",
}: {
  entry?: Entry;
  defaultVisibility?: Input["visibility"];
}) {
  const router = useRouter();
  const [values, setValues] = useState<Input>(entry ?? { ...EMPTY, visibility: defaultVisibility });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body = {
      ...values,
      organization: values.organization || undefined,
      location: values.location || undefined,
      description: values.description || undefined,
      endDate: values.endDate || undefined,
    };
    try {
      const result = entry
        ? await api.PATCH("/career-entries/{id}", {
            params: { path: { id: entry.id } },
            body: { ...body, revision: entry.revision },
          })
        : await api.POST("/career-entries", { body });
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
      router.push(`/career/${result.data.id}`);
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
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="type" label="유형" required error={fieldErrors.type}>
            <select
              id="type"
              className={inputClass}
              value={values.type}
              onChange={(e) => set("type", e.target.value as Input["type"])}
            >
              {CAREER_ENTRY_TYPES.map((t) => (
                <option key={t} value={t}>
                  {careerEntryTypeLabel(t)}
                </option>
              ))}
            </select>
          </Field>
          <Field
            id="visibility"
            label="공개 범위"
            help="문서 생성과 공유 시 적용"
            error={fieldErrors.visibility}
          >
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
        </div>
        <Field
          id="title"
          label="제목"
          required
          help="직책 또는 자격·수상명"
          error={fieldErrors.title}
        >
          <input
            id="title"
            className={inputClass}
            value={values.title}
            maxLength={200}
            aria-invalid={fieldErrors.title ? true : undefined}
            aria-describedby={fieldErrors.title ? "title-error" : undefined}
            onChange={(e) => set("title", e.target.value)}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="organization" label="회사 · 조직" error={fieldErrors.organization}>
            <input
              id="organization"
              className={inputClass}
              value={values.organization ?? ""}
              maxLength={200}
              onChange={(e) => set("organization", e.target.value)}
            />
          </Field>
          <Field id="location" label="위치" error={fieldErrors.location}>
            <input
              id="location"
              className={inputClass}
              value={values.location ?? ""}
              maxLength={200}
              onChange={(e) => set("location", e.target.value)}
            />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="startDate" label="시작일" required error={fieldErrors.startDate}>
            <input
              id="startDate"
              type="date"
              className={inputClass}
              value={values.startDate}
              aria-invalid={fieldErrors.startDate ? true : undefined}
              onChange={(e) => set("startDate", e.target.value)}
            />
          </Field>
          <Field
            id="endDate"
            label="종료일"
            help="진행 중이면 비워 두세요"
            error={fieldErrors.endDate}
          >
            <input
              id="endDate"
              type="date"
              className={inputClass}
              value={values.endDate ?? ""}
              onChange={(e) => set("endDate", e.target.value)}
            />
          </Field>
        </div>
        <Field
          id="description"
          label="설명"
          help="역할과 책임을 사실 중심으로"
          error={fieldErrors.description}
        >
          <textarea
            id="description"
            className={textareaClass}
            value={values.description ?? ""}
            onChange={(e) => set("description", e.target.value)}
          />
        </Field>
      </fieldset>

      <div className="flex gap-3">
        <Button type="submit" loading={submitting}>
          {entry ? "변경 사항 저장" : "경력 저장"}
        </Button>
        <ButtonLink variant="secondary" href={entry ? `/career/${entry.id}` : "/career"}>
          취소
        </ButtonLink>
      </div>
    </form>
  );
}
