"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";

import type { Schema } from "@proofu/contracts";

import { ClaimStatusChip } from "@/components/evidence/chips";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass, textareaClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";
import { PICKER_LIMIT } from "@/lib/limits";
import {
  CLAIM_TYPES,
  RELATIONS,
  SENSITIVITIES,
  claimTypeLabel,
  evidenceTypeLabel,
  relationLabel,
  sensitivityLabel,
  verificationLabel,
} from "@/lib/labels";

type Claim = Schema<"Claim">;
type Evidence = Schema<"Evidence">;
type ClaimSource = Schema<"ClaimSourceInput">;
type Relation = Schema<"EvidenceRelation">;

const CONFIDENCE_BANDS = [
  { value: 0.9, label: "높음 — 근거가 주장을 직접 보여줌" },
  { value: 0.6, label: "보통 — 정황상 뒷받침" },
  { value: 0.3, label: "낮음 — 간접적" },
] as const;

function problemMessage(error: { code?: string; detail?: string } | undefined, fallback: string) {
  if (!error) return fallback;
  if (error.code === "DOMAIN_RULE_VIOLATION")
    return "부분 지지에는 어느 부분을 뒷받침하는지 범위를 적어야 합니다.";
  if (error.code === "CONFLICT_STALE_VERSION")
    return "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.";
  return error.detail ?? fallback;
}

function ClaimForm({
  source,
  defaultText,
  onCreated,
  onCancel,
}: {
  source: ClaimSource;
  defaultText: string;
  onCreated: (c: Claim) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(defaultText);
  const [type, setType] = useState<Schema<"ClaimType">>("FACT");
  const [sensitivity, setSensitivity] = useState<Schema<"Sensitivity">>("INTERNAL");
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const prefix = `claim-${source.id}`;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const result = await api.POST("/claims", {
      body: { text, type, sensitivity, sources: [source] },
    });
    setSubmitting(false);
    if (result.error) {
      setProblem(
        result.error.fieldErrors?.[0]?.message ??
          problemMessage(result.error, "저장하지 못했습니다."),
      );
      return;
    }
    onCreated(result.data);
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="flex flex-col gap-3 rounded-md border border-primary-600/40 bg-surface-050 p-4"
    >
      {problem ? <ErrorState title="주장을 등록할 수 없습니다" description={problem} /> : null}
      <Field id={`${prefix}-text`} label="주장" required help="지원 문서에 쓰고 싶은 한 문장">
        <textarea
          id={`${prefix}-text`}
          className={textareaClass}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field id={`${prefix}-type`} label="구분" help="사실인지, 추론인지, 의견인지">
          <select
            id={`${prefix}-type`}
            className={inputClass}
            value={type}
            onChange={(e) => setType(e.target.value as Schema<"ClaimType">)}
          >
            {CLAIM_TYPES.map((t) => (
              <option key={t} value={t}>
                {claimTypeLabel(t)}
              </option>
            ))}
          </select>
        </Field>
        <Field id={`${prefix}-sensitivity`} label="민감도">
          <select
            id={`${prefix}-sensitivity`}
            className={inputClass}
            value={sensitivity}
            onChange={(e) => setSensitivity(e.target.value as Schema<"Sensitivity">)}
          >
            {SENSITIVITIES.map((s) => (
              <option key={s} value={s}>
                {sensitivityLabel(s)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          주장 등록
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function LinkEvidencePanel({
  claim,
  evidence,
  onLinked,
  onCancel,
}: {
  claim: Claim;
  evidence: Evidence[] | null;
  onLinked: (c: Claim) => void;
  onCancel: () => void;
}) {
  const candidates = (evidence ?? []).filter(
    (e) => !claim.links.some((l) => l.evidenceId === e.id),
  );
  const [evidenceId, setEvidenceId] = useState(candidates[0]?.id ?? "");
  const [relation, setRelation] = useState<Relation>("SUPPORTS");
  const [confidence, setConfidence] = useState<number>(0.6);
  const [scope, setScope] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const prefix = `link-${claim.id}`;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setProblem(null);
    const result = await api.POST("/claims/{id}/evidence", {
      params: { path: { id: claim.id } },
      body: { evidenceId, relation, confidence, scope: scope || undefined },
    });
    setSubmitting(false);
    if (result.error) {
      setProblem(problemMessage(result.error, "연결하지 못했습니다."));
      return;
    }
    onLinked(result.data);
  }

  if (evidence === null)
    return <p className="text-caption text-text-600">Evidence 목록을 불러오는 중…</p>;
  if (candidates.length === 0) {
    return (
      <div className="flex flex-wrap items-center gap-3 rounded-md border border-border-300 bg-surface-050 p-3 text-body">
        <span className="text-text-600">연결할 수 있는 Evidence가 없습니다.</span>
        <Link href="/evidence/new" className="text-primary-600 hover:underline">
          Evidence 추가
        </Link>
        <Button type="button" variant="tertiary" onClick={onCancel}>
          닫기
        </Button>
      </div>
    );
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="flex flex-col gap-3 rounded-md border border-primary-600/40 bg-surface-050 p-3"
    >
      {problem ? <ErrorState title="연결할 수 없습니다" description={problem} /> : null}
      <div className="grid gap-3 sm:grid-cols-3">
        <Field
          id={`${prefix}-evidence`}
          label="Evidence"
          required
          help={
            (evidence ?? []).length >= PICKER_LIMIT ? `최근 ${PICKER_LIMIT}건만 표시` : undefined
          }
        >
          <select
            id={`${prefix}-evidence`}
            className={inputClass}
            value={evidenceId}
            onChange={(e) => setEvidenceId(e.target.value)}
          >
            {candidates.map((e) => (
              <option key={e.id} value={e.id}>
                {e.title} · {evidenceTypeLabel(e.type)} · {verificationLabel(e.verification)}
              </option>
            ))}
          </select>
        </Field>
        <Field id={`${prefix}-relation`} label="관계" required>
          <select
            id={`${prefix}-relation`}
            className={inputClass}
            value={relation}
            onChange={(e) => setRelation(e.target.value as Relation)}
          >
            {RELATIONS.map((r) => (
              <option key={r} value={r}>
                {relationLabel(r)}
              </option>
            ))}
          </select>
        </Field>
        <Field id={`${prefix}-confidence`} label="신뢰도" required>
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
      </div>
      {relation === "PARTIALLY_SUPPORTS" ? (
        <Field id={`${prefix}-scope`} label="적용 범위" required help="근거가 뒷받침하는 부분">
          <input
            id={`${prefix}-scope`}
            className={inputClass}
            value={scope}
            onChange={(e) => setScope(e.target.value)}
          />
        </Field>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" loading={submitting}>
          연결
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  );
}

function ClaimRow({
  claim,
  evidence,
  onChange,
  onDeleted,
}: {
  claim: Claim;
  evidence: Evidence[] | null;
  onChange: (c: Claim) => void;
  onDeleted: () => void;
}) {
  const [linking, setLinking] = useState(false);
  const [busy, setBusy] = useState(false);

  async function unlink(evidenceId: string) {
    setBusy(true);
    const result = await api.DELETE("/claims/{id}/evidence/{evidenceId}", {
      params: { path: { id: claim.id, evidenceId } },
    });
    setBusy(false);
    if (result.error) {
      window.alert(problemMessage(result.error, "해제하지 못했습니다."));
      return;
    }
    onChange(result.data);
  }

  async function remove() {
    if (!window.confirm(`"${claim.text}" 주장을 삭제할까요?\n근거 연결도 함께 사라집니다.`)) return;
    setBusy(true);
    const { error } = await api.DELETE("/claims/{id}", { params: { path: { id: claim.id } } });
    setBusy(false);
    if (error) {
      window.alert(error.detail ?? "삭제하지 못했습니다.");
      return;
    }
    onDeleted();
  }

  return (
    <li className="flex flex-col gap-2 rounded-md border border-border-300 bg-surface-000 p-3">
      <div className="flex flex-wrap items-start gap-2">
        <p className="min-w-0 flex-1 text-body">{claim.text}</p>
        <div className="flex shrink-0 gap-1">
          <Button
            type="button"
            variant="tertiary"
            onClick={() => setLinking((v) => !v)}
            disabled={busy}
          >
            <Icon name="plus" size={16} />
            근거 연결
          </Button>
          <Button type="button" variant="tertiary" onClick={remove} loading={busy}>
            삭제
          </Button>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Chip>{claimTypeLabel(claim.type)}</Chip>
        <ClaimStatusChip value={claim.status} />
        {claim.links.map((l) => (
          <span
            key={`${l.evidenceId}-${l.relation}`}
            className="inline-flex h-6 items-center gap-1 rounded-sm border border-border-300 bg-surface-050 pl-2 text-caption"
          >
            <Link href={`/evidence/${l.evidenceId}`} className="hover:underline">
              {l.evidenceTitle}
            </Link>
            <span className="text-text-600">· {relationLabel(l.relation)}</span>
            <button
              type="button"
              aria-label={`${l.evidenceTitle} 연결 해제`}
              className="flex h-6 w-6 items-center justify-center rounded-sm text-text-600 hover:bg-surface-100"
              onClick={() => unlink(l.evidenceId)}
              disabled={busy}
            >
              ×
            </button>
          </span>
        ))}
      </div>
      {linking ? (
        <LinkEvidencePanel
          claim={claim}
          evidence={evidence}
          onLinked={(c) => {
            onChange(c);
            setLinking(false);
          }}
          onCancel={() => setLinking(false)}
        />
      ) : null}
    </li>
  );
}

/**
 * Claims made about one record (achievement, project or career entry) with inline
 * creation and evidence linking. Claim status is derived server-side from the links.
 */
export function ClaimPanel({
  source,
  initialClaims,
  defaultText,
  heading = "주장",
}: {
  source: ClaimSource;
  initialClaims: Claim[];
  defaultText: string;
  heading?: string;
}) {
  const [claims, setClaims] = useState(initialClaims);
  const [adding, setAdding] = useState(false);
  const [evidence, setEvidence] = useState<Evidence[] | null>(null);

  // Evidence options are loaded once, when the first claim exists (that is when linking becomes possible).
  useEffect(() => {
    if (claims.length === 0 || evidence !== null) return;
    api
      .GET("/evidence", { params: { query: { limit: PICKER_LIMIT } } })
      .then((r) => setEvidence(r.data?.items ?? []))
      .catch(() => setEvidence([]));
  }, [claims.length, evidence]);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <h3 className="text-caption font-semibold text-text-600">
          {heading} {claims.length > 0 ? `(${claims.length})` : ""}
        </h3>
        {!adding ? (
          <Button type="button" variant="tertiary" onClick={() => setAdding(true)}>
            <Icon name="plus" size={16} />
            주장 등록
          </Button>
        ) : null}
      </div>
      {adding ? (
        <ClaimForm
          source={source}
          defaultText={defaultText}
          onCreated={(c) => {
            setClaims((list) => [...list, c]);
            setAdding(false);
          }}
          onCancel={() => setAdding(false)}
        />
      ) : null}
      {claims.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {claims.map((c) => (
            <ClaimRow
              key={c.id}
              claim={c}
              evidence={evidence}
              onChange={(next) =>
                setClaims((list) => list.map((x) => (x.id === next.id ? next : x)))
              }
              onDeleted={() => setClaims((list) => list.filter((x) => x.id !== c.id))}
            />
          ))}
        </ul>
      ) : !adding ? (
        <p className="text-caption text-text-600">
          아직 주장이 없습니다. 지원 문서에 쓸 문장을 등록하고 Evidence를 연결하면 근거 연결률에
          반영됩니다.
        </p>
      ) : null}
    </div>
  );
}
