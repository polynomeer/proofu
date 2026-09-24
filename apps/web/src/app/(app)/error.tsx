"use client";

import { useEffect } from "react";

import { Button, ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageHeader } from "@/components/ui/PageHeader";

/**
 * Anything a screen throws lands here instead of a blank page: what failed, what the user can
 * do, and a retry that re-renders the segment (docs/ux/screen-specifications.md §공통 상태).
 * The message itself is never shown — it can carry internals — only the digest.
 */
export default function AppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("screen failed", error.digest ?? error.message);
  }, [error]);

  return (
    <>
      <PageHeader title="화면을 열지 못했습니다" description="저장된 데이터는 그대로입니다." />
      <div className="flex flex-col items-start gap-4">
        <ErrorState
          title="요청을 처리하지 못했습니다"
          description="일시적인 문제이거나 서버에 연결하지 못한 상태입니다. 다시 시도해도 같은 화면이 나오면 잠시 후 다시 열어 보세요."
        />
        {error.digest ? (
          <p className="text-caption text-text-600">
            문의할 때 이 코드를 함께 알려 주세요: <code>{error.digest}</code>
          </p>
        ) : null}
        <div className="flex gap-2">
          <Button onClick={reset}>다시 시도</Button>
          <ButtonLink variant="secondary" href="/">
            대시보드로
          </ButtonLink>
        </div>
      </div>
    </>
  );
}
