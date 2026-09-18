"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button, ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { toDateInput } from "@/lib/format";
import { EVIDENCE_TYPES, SENSITIVITIES, evidenceTypeLabel, sensitivityLabel } from "@/lib/labels";

type Evidence = Schema<"Evidence">;
type Input = Schema<"EvidenceInput">;

const EMPTY: Input = { type: "URL", title: "", uri: "", body: "", sensitivity: "INTERNAL" };

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

export function EvidenceForm({ evidence }: { evidence?: Evidence }) {
  const router = useRouter();
  const [values, setValues] = useState<Input>(evidence ?? EMPTY);
  const [capturedDate, setCapturedDate] = useState(
    evidence ? toDateInput(evidence.capturedAt) : "",
  );
  const [verified, setVerified] = useState(evidence?.verification === "USER_VERIFIED");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const isNote = values.type === "NOTE";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      uri: isNote ? null : values.uri || null,
      body: values.body || null,
      capturedAt: capturedDate ? new Date(`${capturedDate}T00:00:00`).toISOString() : undefined,
      verification: verified ? "USER_VERIFIED" : "UNVERIFIED",
    };
    try {
      const result = evidence
        ? await api.PATCH("/evidence/{id}", {
            params: { path: { id: evidence.id } },
            body: { ...body, revision: evidence.revision },
          })
        : await api.POST("/evidence", { body });
      if (result.error) {
        const errors = fieldErrorsOf(result.error);
        setFieldErrors(errors);
        setProblem(
          result.error.code === "DOMAIN_RULE_VIOLATION"
            ? isNote
              ? "메모에는 본문이 필요합니다."
              : "https:// 로 시작하는 전체 주소를 입력하세요."
            : result.error.code === "CONFLICT_STALE_VERSION"
              ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 편집하세요."
              : Object.keys(errors).length > 0
                ? "표시된 항목을 확인하세요."
                : (result.error.detail ?? "저장하지 못했습니다."),
        );
        return;
      }
      router.push(`/evidence/${result.data.id}`);
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
        <legend className="px-1 text-card-title">근거 자료</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="type"
            label="유형"
            required
            help="파일 업로드는 준비 중"
            error={fieldErrors.type}
          >
            <select
              id="type"
              className={inputClass}
              value={values.type}
              disabled={!!evidence}
              onChange={(e) => set("type", e.target.value as Input["type"])}
            >
              {EVIDENCE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {evidenceTypeLabel(t)}
                </option>
              ))}
            </select>
          </Field>
          <Field
            id="sensitivity"
            label="민감도"
            help="기밀·제한은 AI 처리에서 제외"
            error={fieldErrors.sensitivity}
          >
            <select
              id="sensitivity"
              className={inputClass}
              value={values.sensitivity ?? "INTERNAL"}
              onChange={(e) => set("sensitivity", e.target.value as Input["sensitivity"])}
            >
              {SENSITIVITIES.map((s) => (
                <option key={s} value={s}>
                  {sensitivityLabel(s)}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field id="title" label="제목" required error={fieldErrors.title}>
          <input
            id="title"
            className={inputClass}
            value={values.title}
            maxLength={200}
            aria-invalid={fieldErrors.title ? true : undefined}
            onChange={(e) => set("title", e.target.value)}
          />
        </Field>
        {!isNote ? (
          <Field
            id="uri"
            label="주소"
            required
            help="https:// 포함 전체 URL"
            error={fieldErrors.uri}
          >
            <input
              id="uri"
              type="url"
              className={inputClass}
              value={values.uri ?? ""}
              aria-invalid={fieldErrors.uri ? true : undefined}
              onChange={(e) => set("uri", e.target.value)}
            />
          </Field>
        ) : null}
        <Field
          id="body"
          label={isNote ? "본문" : "설명"}
          required={isNote}
          help={isNote ? "사실 위주로, 날짜와 수치를 포함" : "무엇을 증명하는 자료인지"}
          error={fieldErrors.body}
        >
          <textarea
            id="body"
            className={textareaClass}
            value={values.body ?? ""}
            aria-invalid={fieldErrors.body ? true : undefined}
            onChange={(e) => set("body", e.target.value)}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="capturedAt" label="수집일" help="비우면 오늘" error={fieldErrors.capturedAt}>
            <input
              id="capturedAt"
              type="date"
              className={inputClass}
              value={capturedDate}
              onChange={(e) => setCapturedDate(e.target.value)}
            />
          </Field>
          <div className="flex items-end pb-2">
            <label className="flex items-center gap-2 text-body">
              <input
                type="checkbox"
                className="h-4 w-4 accent-primary-600"
                checked={verified}
                onChange={(e) => setVerified(e.target.checked)}
              />
              내용을 직접 확인했습니다 (검증됨)
            </label>
          </div>
        </div>
      </fieldset>

      <div className="flex gap-3">
        <Button type="submit" loading={submitting}>
          {evidence ? "변경 사항 저장" : "Evidence 저장"}
        </Button>
        <ButtonLink variant="secondary" href={evidence ? `/evidence/${evidence.id}` : "/evidence"}>
          취소
        </ButtonLink>
      </div>
    </form>
  );
}
