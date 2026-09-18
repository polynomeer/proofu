"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";

type Review = Schema<"Review">;
type Input = Schema<"ReviewInput">;
type Confidence = Schema<"ReviewConfidence">;

const CONFIDENCE: { value: Confidence; label: string }[] = [
  { value: "LOW", label: "낮음 — 추정" },
  { value: "MEDIUM", label: "보통 — 정황이 있음" },
  { value: "HIGH", label: "높음 — 확인 가능한 근거" },
];

const EMPTY: Input = {
  observedFact: "",
  hypothesis: "",
  rationale: "",
  confidence: "LOW",
  improvementAction: "",
  verificationPlan: "",
};

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

/** Warnings come from the domain (Review.definitiveLanguage); the UI never re-derives them. */
function Warnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null;
  return (
    <p className="flex flex-wrap items-center gap-2 rounded-md bg-warning-050 px-3 py-2 text-caption text-warning-700">
      <Icon name="alert" size={16} />
      원인을 단정하는 표현이 있습니다:
      {warnings.map((w) => (
        <span key={w} className="font-semibold">
          “{w}”
        </span>
      ))}
      <span className="text-text-600">— 탈락 원인은 확인할 수 없으니 가설로 표현해 주세요.</span>
    </p>
  );
}

function ReviewForm({
  applicationId,
  initial,
  onSaved,
  onCancel,
}: {
  applicationId: string;
  initial?: Review;
  onSaved: (r: Review) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Input>(initial ?? EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof Input>(key: K, value: Input[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const prefix = initial ? `review-${initial.id}` : "review-new";

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const body: Input = {
      ...values,
      rationale: values.rationale || undefined,
      improvementAction: values.improvementAction || undefined,
      verificationPlan: values.verificationPlan || undefined,
    };
    const result = initial
      ? await api.PATCH("/reviews/{id}", {
          params: { path: { id: initial.id } },
          body: { ...body, version: initial.version },
        })
      : await api.POST("/applications/{id}/reviews", {
          params: { path: { id: applicationId } },
          body,
        });
    setSubmitting(false);
    if (result.error) {
      const errors = fieldErrorsOf(result.error);
      setFieldErrors(errors);
      setProblem(
        result.error.code === "DOMAIN_RULE_VIOLATION"
          ? "서류 결과가 기록된 뒤에만 회고를 쓸 수 있습니다."
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
        id={`${prefix}-fact`}
        label="관찰 사실"
        required
        help="직접 확인한 결과만 (통보 시각, 채널, 문구)"
        error={fieldErrors.observedFact}
      >
        <input
          id={`${prefix}-fact`}
          className={inputClass}
          value={values.observedFact}
          aria-invalid={fieldErrors.observedFact ? true : undefined}
          onChange={(e) => set("observedFact", e.target.value)}
        />
      </Field>
      <Field
        id={`${prefix}-hypothesis`}
        label="가설"
        required
        help="가능하지만 확인되지 않은 원인. '~였을 수 있음'처럼"
        error={fieldErrors.hypothesis}
      >
        <textarea
          id={`${prefix}-hypothesis`}
          className={textareaClass}
          value={values.hypothesis}
          aria-invalid={fieldErrors.hypothesis ? true : undefined}
          onChange={(e) => set("hypothesis", e.target.value)}
        />
      </Field>
      <Field
        id={`${prefix}-rationale`}
        label="근거"
        help="가설을 지지하거나 반박하는 정보"
        error={fieldErrors.rationale}
      >
        <textarea
          id={`${prefix}-rationale`}
          className={textareaClass}
          value={values.rationale ?? ""}
          onChange={(e) => set("rationale", e.target.value)}
        />
      </Field>
      <Field id={`${prefix}-confidence`} label="신뢰도" required>
        <select
          id={`${prefix}-confidence`}
          className={inputClass}
          value={values.confidence ?? "LOW"}
          onChange={(e) => set("confidence", e.target.value as Confidence)}
        >
          {CONFIDENCE.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id={`${prefix}-action`}
          label="개선 행동"
          help="다음 지원에서 실행할 변경"
          error={fieldErrors.improvementAction}
        >
          <textarea
            id={`${prefix}-action`}
            className={textareaClass}
            value={values.improvementAction ?? ""}
            onChange={(e) => set("improvementAction", e.target.value)}
          />
        </Field>
        <Field
          id={`${prefix}-plan`}
          label="검증 계획"
          help="가설을 확인할 다음 관찰"
          error={fieldErrors.verificationPlan}
        >
          <textarea
            id={`${prefix}-plan`}
            className={textareaClass}
            value={values.verificationPlan ?? ""}
            onChange={(e) => set("verificationPlan", e.target.value)}
          />
        </Field>
      </div>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          {initial ? "변경 사항 저장" : "회고 저장"}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function confidenceLabel(c: Confidence) {
  return CONFIDENCE.find((x) => x.value === c)?.label.split(" — ")[0] ?? c;
}

function ReviewCard({
  review,
  onEdit,
  onDeleted,
}: {
  review: Review;
  onEdit: () => void;
  onDeleted: () => void;
}) {
  const [busy, setBusy] = useState(false);
  async function remove() {
    if (!window.confirm("이 회고를 삭제할까요? 지원 상태는 바뀌지 않습니다.")) return;
    setBusy(true);
    const { error } = await api.DELETE("/reviews/{id}", { params: { path: { id: review.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }
  const rows: [string, string | null | undefined][] = [
    ["관찰 사실", review.observedFact],
    ["가설", review.hypothesis],
    ["근거", review.rationale],
    ["개선 행동", review.improvementAction],
    ["검증 계획", review.verificationPlan],
  ];
  return (
    <li className="flex flex-col gap-3 rounded-md border border-border-300 bg-surface-000 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex items-center gap-2 text-caption text-text-600">
          <Chip
            tone={
              review.confidence === "HIGH"
                ? "verified"
                : review.confidence === "MEDIUM"
                  ? "neutral"
                  : "review"
            }
          >
            신뢰도 {confidenceLabel(review.confidence)}
          </Chip>
          {formatDateTime(review.createdAt)}
        </span>
        <span className="flex gap-1">
          <Button type="button" variant="tertiary" onClick={onEdit}>
            편집
          </Button>
          <Button type="button" variant="tertiary" onClick={remove} loading={busy}>
            삭제
          </Button>
        </span>
      </div>
      <Warnings warnings={review.warnings} />
      <dl className="grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 text-body">
        {rows
          .filter(([, v]) => v)
          .map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-text-600">{label}</dt>
              <dd className="whitespace-pre-line">{value}</dd>
            </div>
          ))}
      </dl>
    </li>
  );
}

/**
 * A02 review section. The first saved review moves the application to REVIEWED, so the
 * page refreshes to pick up the new status and history.
 */
export function ReviewSection({
  applicationId,
  initialReviews,
  canReview,
}: {
  applicationId: string;
  initialReviews: Review[];
  canReview: boolean;
}) {
  const router = useRouter();
  const [reviews, setReviews] = useState(initialReviews);
  const [editing, setEditing] = useState<"new" | string | null>(null);

  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-section-title">서류 회고</h2>
        {canReview && editing === null ? (
          <Button type="button" variant="secondary" onClick={() => setEditing("new")}>
            <Icon name="plus" size={16} />
            회고 쓰기
          </Button>
        ) : null}
      </div>
      <p className="mb-4 text-caption text-text-600">
        확정된 사실과 가설을 분리해 기록합니다. 탈락 원인은 알 수 없으므로 가능성으로 표현하고, 다음
        지원에서 확인할 계획을 남깁니다.
      </p>

      {editing === "new" ? (
        <ReviewForm
          applicationId={applicationId}
          onSaved={(r) => {
            setReviews((list) => [...list, r]);
            setEditing(null);
            router.refresh();
          }}
          onCancel={() => setEditing(null)}
        />
      ) : null}

      {reviews.length === 0 && editing !== "new" ? (
        <EmptyState
          title={canReview ? "아직 회고가 없습니다" : "서류 결과가 나오면 회고를 쓸 수 있습니다"}
          description={
            canReview
              ? "관찰한 사실 하나와 가설 하나면 충분합니다. 첫 회고를 저장하면 이 지원은 회고 완료로 이동합니다."
              : "서류 탈락 또는 무응답으로 상태를 바꾼 뒤 회고를 기록하세요."
          }
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {reviews.map((r) =>
            editing === r.id ? (
              <li key={r.id}>
                <ReviewForm
                  applicationId={applicationId}
                  initial={r}
                  onSaved={(saved) => {
                    setReviews((list) => list.map((x) => (x.id === saved.id ? saved : x)));
                    setEditing(null);
                  }}
                  onCancel={() => setEditing(null)}
                />
              </li>
            ) : (
              <ReviewCard
                key={r.id}
                review={r}
                onEdit={() => setEditing(r.id)}
                onDeleted={() => setReviews((list) => list.filter((x) => x.id !== r.id))}
              />
            ),
          )}
        </ul>
      )}
    </section>
  );
}
