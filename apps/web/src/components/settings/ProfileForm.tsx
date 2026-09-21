"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import type { ProblemDetail, Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

type Profile = Schema<"Profile">;
type Link = Schema<"ProfileLink">;

type Values = {
  fullName: string;
  headline: string;
  email: string;
  phone: string;
  location: string;
  links: Link[];
};

const MAX_LINKS = 5;

function fieldErrorsOf(problem: ProblemDetail | undefined): Record<string, string> {
  return Object.fromEntries((problem?.fieldErrors ?? []).map((e) => [e.field, e.message]));
}

/** S01 profile: what document headers carry. Contact data stays here and in exports — never in AI context. */
export function ProfileForm({ initial }: { initial: Profile | null }) {
  const router = useRouter();
  const [values, setValues] = useState<Values>({
    fullName: initial?.fullName ?? "",
    headline: initial?.headline ?? "",
    email: initial?.email ?? "",
    phone: initial?.phone ?? "",
    location: initial?.location ?? "",
    links: initial?.links ?? [],
  });
  const [version, setVersion] = useState(initial?.version);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const set = <K extends keyof Values>(key: K, value: Values[K]) =>
    setValues((v) => ({ ...v, [key]: value }));
  const setLink = (i: number, patch: Partial<Link>) =>
    set(
      "links",
      values.links.map((l, j) => (i === j ? { ...l, ...patch } : l)),
    );

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setProblem(null);
    setFieldErrors({});
    const { data, error } = await api.PUT("/me/profile", {
      body: {
        fullName: values.fullName,
        headline: values.headline || null,
        email: values.email || null,
        phone: values.phone || null,
        location: values.location || null,
        links: values.links.filter((l) => l.label.trim() || l.url.trim()),
        version,
      },
    });
    setSaving(false);
    if (!data) {
      const errors = fieldErrorsOf(error);
      setFieldErrors(errors);
      setProblem(
        error?.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요."
          : error?.code === "DOMAIN_RULE_VIOLATION"
            ? (error.detail ?? "입력값을 확인하세요.")
            : Object.keys(errors).length > 0
              ? "표시된 항목을 확인하세요."
              : (error?.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    setVersion(data.version);
    setSavedAt(data.updatedAt);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id="profile-name" label="이름" required error={fieldErrors.fullName}>
          <input
            id="profile-name"
            className={inputClass}
            value={values.fullName}
            maxLength={120}
            aria-invalid={fieldErrors.fullName ? true : undefined}
            onChange={(e) => set("fullName", e.target.value)}
          />
        </Field>
        <Field id="profile-headline" label="한 줄 소개" help="예: B2B SaaS 프로덕트 매니저">
          <input
            id="profile-headline"
            className={inputClass}
            value={values.headline}
            maxLength={200}
            onChange={(e) => set("headline", e.target.value)}
          />
        </Field>
        <Field
          id="profile-email"
          label="이메일"
          help="ATS가 파싱하는 연락처"
          error={fieldErrors.email}
        >
          <input
            id="profile-email"
            type="email"
            className={inputClass}
            value={values.email}
            maxLength={320}
            onChange={(e) => set("email", e.target.value)}
          />
        </Field>
        <Field id="profile-phone" label="전화" help="숫자, +, 공백, - 만" error={fieldErrors.phone}>
          <input
            id="profile-phone"
            type="tel"
            className={inputClass}
            value={values.phone}
            maxLength={40}
            onChange={(e) => set("phone", e.target.value)}
          />
        </Field>
        <Field id="profile-location" label="지역">
          <input
            id="profile-location"
            className={inputClass}
            value={values.location}
            maxLength={120}
            onChange={(e) => set("location", e.target.value)}
          />
        </Field>
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="text-body font-semibold">
          링크{" "}
          <span className="font-normal text-text-600">최대 {MAX_LINKS}개 · https:// 로 시작</span>
        </legend>
        {values.links.map((l, i) => (
          <div key={i} className="grid gap-2 sm:grid-cols-[160px_1fr_auto]">
            <input
              aria-label={`링크 ${i + 1} 이름`}
              className={inputClass}
              placeholder="GitHub"
              value={l.label}
              maxLength={80}
              onChange={(e) => setLink(i, { label: e.target.value })}
            />
            <input
              aria-label={`링크 ${i + 1} 주소`}
              className={inputClass}
              placeholder="https://"
              value={l.url}
              onChange={(e) => setLink(i, { url: e.target.value })}
            />
            <Button
              type="button"
              variant="tertiary"
              onClick={() =>
                set(
                  "links",
                  values.links.filter((_, j) => j !== i),
                )
              }
            >
              삭제
            </Button>
          </div>
        ))}
        {values.links.length < MAX_LINKS ? (
          <Button
            type="button"
            variant="tertiary"
            className="self-start"
            onClick={() => set("links", [...values.links, { label: "", url: "" }])}
          >
            <Icon name="plus" size={16} />
            링크 추가
          </Button>
        ) : null}
      </fieldset>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={saving}>
          프로필 저장
        </Button>
        {savedAt ? <span className="text-caption text-success-700">저장했습니다.</span> : null}
        {version ? <span className="text-caption text-text-600">version {version}</span> : null}
      </div>
    </form>
  );
}
