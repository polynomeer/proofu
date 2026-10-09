"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Field, inputClass } from "@/components/ui/Field";
import { api } from "@/lib/api";

const CONFIRM_WORD = "삭제";

/**
 * S01 account deletion. The API needs a recent login; on REAUTHENTICATION_REQUIRED the browser
 * goes through the IdP again and returns here. After acceptance the session is ended locally.
 */
export function DeleteAccount({ canSignOut }: { canSignOut: boolean }) {
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    const { data, error: problem, response } = await api.DELETE("/me", {});
    if (!data) {
      setBusy(false);
      if (problem?.code === "REAUTHENTICATION_REQUIRED" || response.status === 401) {
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign("/auth/login?prompt=login&return=/settings");
        return;
      }
      setError(problem?.detail ?? "삭제 요청을 접수하지 못했습니다.");
      return;
    }
    // The account is already refused by the API; end this browser's session too.
    if (canSignOut) {
      await fetch("/auth/logout", { method: "POST", redirect: "manual" }).catch(() => undefined);
    }
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/auth/signed-out");
  }

  return (
    <section className="rounded-md border border-warning-600/40 bg-surface-000 p-4 md:p-6">
      <h2 className="text-card-title">계정 삭제</h2>
      <p className="mt-1 text-caption text-text-600">
        경력·Evidence·공고·지원·문서·제출 스냅샷·내보내기 파일을 모두 삭제합니다. 되돌릴 수 없고,
        백업에는 35일 뒤에 반영됩니다. 최근 10분 안에 로그인한 상태여야 합니다.
      </p>
      {!open ? (
        <Button variant="danger" className="mt-3" onClick={() => setOpen(true)}>
          계정 삭제…
        </Button>
      ) : (
        <div className="mt-3 flex flex-col gap-3">
          {error ? <ErrorState title="삭제하지 못했습니다" description={error} /> : null}
          <Field id="delete-confirm" label={`확인을 위해 “${CONFIRM_WORD}”를 입력하세요`} required>
            <input
              id="delete-confirm"
              className={`${inputClass} w-48`}
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoComplete="off"
            />
          </Field>
          <div className="flex gap-2">
            <Button
              variant="danger"
              onClick={confirm}
              loading={busy}
              disabled={typed !== CONFIRM_WORD}
            >
              영구 삭제
            </Button>
            <Button
              variant="tertiary"
              onClick={() => {
                setOpen(false);
                setTyped("");
              }}
            >
              취소
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
