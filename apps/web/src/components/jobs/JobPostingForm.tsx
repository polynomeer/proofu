"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail } from "@proofu/contracts";

import { Button, ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";

type Values = { company: string; roleTitle: string; sourceUrl: string; text: string };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

/**
 * Manual import (source MANUAL_TEXT). Re-pasting an already tracked url adds a snapshot to
 * that posting instead of creating a new one; identical text is kept as a single snapshot.
 */
export function JobPostingForm({ initial }: { initial?: Partial<Values> }) {
  const router = useRouter();
  const [values, setValues] = useState<Values>({
    company: initial?.company ?? "",
    roleTitle: initial?.roleTitle ?? "",
    sourceUrl: initial?.sourceUrl ?? "",
    text: "",
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = (key: keyof Values, value: string) => setValues((v) => ({ ...v, [key]: value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    try {
      const result = await api.POST("/job-postings/import", {
        body: {
          source: "MANUAL_TEXT",
          company: values.company,
          roleTitle: values.roleTitle,
          text: values.text,
          sourceUrl: values.sourceUrl || null,
        },
      });
      if (result.error) {
        const errors = fieldErrorsOf(result.error);
        setFieldErrors(errors);
        setProblem(
          result.error.code === "DOMAIN_RULE_VIOLATION"
            ? "공고 본문이 비어 있거나 URL이 https:// 로 시작하지 않습니다."
            : Object.keys(errors).length > 0
              ? "표시된 항목을 확인하세요."
              : (result.error.detail ?? "저장하지 못했습니다."),
        );
        return;
      }
      const { postingId, snapshotId, snapshotCreated } = result.data;
      router.push(
        `/jobs/${postingId}?snapshot=${snapshotId}${snapshotCreated ? "" : "&imported=same"}`,
      );
      router.refresh();
    } catch {
      setProblem("API 서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-3xl flex-col gap-6">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}

      <fieldset className="flex flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-4">
        <legend className="px-1 text-card-title">공고 정보</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="company" label="회사" required error={fieldErrors.company}>
            <input
              id="company"
              className={inputClass}
              value={values.company}
              maxLength={200}
              aria-invalid={fieldErrors.company ? true : undefined}
              onChange={(e) => set("company", e.target.value)}
            />
          </Field>
          <Field id="roleTitle" label="직무" required error={fieldErrors.roleTitle}>
            <input
              id="roleTitle"
              className={inputClass}
              value={values.roleTitle}
              maxLength={200}
              aria-invalid={fieldErrors.roleTitle ? true : undefined}
              onChange={(e) => set("roleTitle", e.target.value)}
            />
          </Field>
        </div>
        <Field
          id="sourceUrl"
          label="공고 URL"
          help="같은 URL을 다시 붙여넣으면 새 버전으로 쌓입니다"
          error={fieldErrors.sourceUrl}
        >
          <input
            id="sourceUrl"
            type="url"
            className={inputClass}
            placeholder="https://"
            value={values.sourceUrl}
            onChange={(e) => set("sourceUrl", e.target.value)}
          />
        </Field>
        <Field
          id="text"
          label="공고 본문"
          required
          help="채용 페이지의 본문을 그대로 붙여넣으세요. 원문은 수정 없이 불변 스냅샷으로 보존됩니다."
          error={fieldErrors.text}
        >
          <textarea
            id="text"
            className={`${textareaClass} min-h-80 font-mono text-caption leading-relaxed`}
            value={values.text}
            aria-invalid={fieldErrors.text ? true : undefined}
            onChange={(e) => set("text", e.target.value)}
          />
        </Field>
        <p className="text-caption text-text-600">
          URL 자동 수집과 career-ops 연동은 준비 중입니다. 지금은 붙여넣기만 지원합니다.
        </p>
      </fieldset>

      <div className="flex gap-3">
        <Button type="submit" loading={submitting}>
          공고 저장
        </Button>
        <ButtonLink variant="secondary" href="/jobs">
          취소
        </ButtonLink>
      </div>
    </form>
  );
}
