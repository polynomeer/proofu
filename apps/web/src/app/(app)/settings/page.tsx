import type { Metadata } from "next";

import { ProfileForm } from "@/components/settings/ProfileForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "설정" };
export const dynamic = "force-dynamic";

/** S01: profile now; visibility defaults, AI consent, retention and account deletion follow OIDC. */
export default async function SettingsPage() {
  const { data: profile } = await api.GET("/me/profile").catch(() => ({ data: undefined }));

  return (
    <>
      <PageHeader title="설정" description="계정과 개인정보" />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <section className="rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-section-title">프로필</h2>
          <p className="mt-1 mb-4 text-caption text-text-600">
            내보내는 문서의 머리글에 들어갑니다. 이 정보는 AI에 전송되지 않고 문서 버전에도 저장되지
            않습니다. 저장하면 이후 내보내기부터 새 머리글을 씁니다.
          </p>
          <ProfileForm initial={profile ?? null} />
        </section>
        <aside className="flex h-fit flex-col gap-4">
          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">머리글 미리보기</h2>
            {profile ? (
              <div className="mt-2 flex flex-col gap-1 text-body">
                <span className="text-section-title">{profile.fullName}</span>
                {profile.headline ? (
                  <span className="text-caption text-text-600">{profile.headline}</span>
                ) : null}
                {profile.contactLine ? (
                  <span className="text-caption">{profile.contactLine}</span>
                ) : null}
                {(profile.links ?? []).map((l) => (
                  <span key={l.url} className="text-caption break-all">
                    {l.label}: {l.url}
                  </span>
                ))}
              </div>
            ) : (
              <p className="mt-2 text-caption text-text-600">
                아직 프로필이 없습니다. 이름과 연락처를 저장하면 ATS 검사의 연락처 항목이
                통과합니다.
              </p>
            )}
          </div>
          <div className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">준비 중</h2>
            <ul className="mt-2 flex flex-col gap-1 text-caption text-text-600">
              <li>공개 범위 기본값</li>
              <li>AI 처리 동의 (기밀·제한 데이터)</li>
              <li>보존 기간, 데이터 내보내기, 계정 삭제 — OIDC 도입 후</li>
            </ul>
          </div>
        </aside>
      </div>
    </>
  );
}
