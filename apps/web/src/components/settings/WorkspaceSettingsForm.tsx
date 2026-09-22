"use client";

import { useState, type FormEvent } from "react";

import type { Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { VISIBILITIES, visibilityLabel } from "@/lib/labels";

type Settings = Schema<"WorkspaceSettings">;
type Visibility = Schema<"Visibility">;

/**
 * S01: AI consent for CONFIDENTIAL records, the default visibility of new records, and the
 * retention windows the worker's sweep applies.
 * Granting consent needs a recent login; a 401 sends the browser through the IdP and back.
 */
export function WorkspaceSettingsForm({ initial }: { initial: Settings }) {
  const [consent, setConsent] = useState(initial.aiConsent === "CONFIDENTIAL");
  const [visibility, setVisibility] = useState<Visibility>(initial.defaultVisibility);
  const [trashDays, setTrashDays] = useState(String(initial.retention.trashDays));
  const [exportDays, setExportDays] = useState(String(initial.retention.exportDays));
  const [version, setVersion] = useState(initial.version);
  const [consentAt, setConsentAt] = useState(initial.aiConsentAt ?? null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    const retention = { trashDays: Number(trashDays), exportDays: Number(exportDays) };
    if (
      !Number.isInteger(retention.trashDays) ||
      retention.trashDays < 7 ||
      retention.trashDays > 365 ||
      !Number.isInteger(retention.exportDays) ||
      retention.exportDays < 1 ||
      retention.exportDays > 90
    ) {
      setSaving(false);
      setError("휴지통 보존은 7–365일, 내보내기 파일 보존은 1–90일 사이여야 합니다.");
      return;
    }
    const { data, error: problem } = await api.PUT("/me/settings", {
      body: {
        aiConsent: consent ? "CONFIDENTIAL" : "NONE",
        defaultVisibility: visibility,
        retention,
        version,
      },
    });
    setSaving(false);
    if (!data) {
      if (problem?.code === "REAUTHENTICATION_REQUIRED") {
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign("/auth/login?prompt=login&return=/settings");
        return;
      }
      setError(
        problem?.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요."
          : (problem?.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    setVersion(data.version);
    setConsentAt(data.aiConsentAt ?? null);
    setSaved(true);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      {error ? <ErrorState title="저장할 수 없습니다" description={error} /> : null}

      <fieldset className="flex flex-col gap-2">
        <legend className="text-body font-semibold">AI 처리 동의</legend>
        <p className="text-caption text-text-600">
          공개·내부 민감도 기록은 항상 AI가 볼 수 있습니다. 아래에 동의하면 <strong>기밀</strong>{" "}
          민감도의 주장·Evidence도 매칭 설명, 초안, 문장 개선에 쓰입니다. <strong>제한</strong>{" "}
          민감도(신분증·건강·계정 정보)는 동의와 무관하게 절대 전송되지 않습니다. 모델 제공자는
          Anthropic이며 국외 서버에서 처리되고 입력은 학습에 쓰이지 않습니다(도움말 참고).
        </p>
        <label className="flex items-start gap-2 text-body">
          <input
            type="checkbox"
            className="mt-1"
            checked={consent}
            onChange={(e) => setConsent(e.target.checked)}
          />
          <span>
            기밀 민감도 기록을 AI 처리에 포함하는 데 동의합니다.
            {consentAt ? (
              <span className="block text-caption text-text-600">
                동의 {formatDateTime(consentAt)}
              </span>
            ) : null}
          </span>
        </label>
        {consent && !initial.aiConsent.startsWith("CONF") ? (
          <p className="text-caption text-warning-700">
            동의를 저장하려면 최근 10분 안에 로그인한 상태여야 합니다. 아니면 다시 로그인하게
            됩니다.
          </p>
        ) : null}
      </fieldset>

      <Field
        id="default-visibility"
        label="새 기록의 공개 범위 기본값"
        help="경력·프로젝트를 만들 때 미리 선택되는 값"
      >
        <select
          id="default-visibility"
          className={`${inputClass} w-56`}
          value={visibility}
          onChange={(e) => setVisibility(e.target.value as Visibility)}
        >
          {VISIBILITIES.map((v) => (
            <option key={v} value={v}>
              {visibilityLabel(v)}
            </option>
          ))}
        </select>
      </Field>

      <fieldset className="flex flex-col gap-3">
        <legend className="text-body font-semibold">보존 기간</legend>
        <p className="text-caption text-text-600">
          삭제한 경력·프로젝트·성과·주장·Evidence·요구사항은 휴지통에 머문 뒤 영구 삭제되고, 만든
          문서 파일과 전체 내보내기 ZIP은 기간이 지나면 만료됩니다. 공고·지원·문서와 제출 스냅샷은
          계정을 삭제할 때까지 남습니다.
        </p>
        <div className="flex flex-wrap gap-4">
          <Field id="trash-days" label="휴지통 보존 (일)" help="7–365일. 기본 30일">
            <input
              id="trash-days"
              type="number"
              min={7}
              max={365}
              className={`${inputClass} w-28`}
              value={trashDays}
              onChange={(e) => setTrashDays(e.target.value)}
            />
          </Field>
          <Field id="export-days" label="내보내기 파일 보존 (일)" help="1–90일. 기본 7일">
            <input
              id="export-days"
              type="number"
              min={1}
              max={90}
              className={`${inputClass} w-28`}
              value={exportDays}
              onChange={(e) => setExportDays(e.target.value)}
            />
          </Field>
        </div>
      </fieldset>

      <div className="flex items-center gap-3">
        <Button type="submit" loading={saving}>
          설정 저장
        </Button>
        {saved ? <span className="text-caption text-success-700">저장했습니다.</span> : null}
      </div>
    </form>
  );
}
