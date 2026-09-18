"use client";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

export function RunMatchingButton({
  applicationId,
  hasRun,
}: {
  applicationId: string;
  hasRun: boolean;
}) {
  return (
    <AsyncJobButton
      variant={hasRun ? "secondary" : "primary"}
      label={hasRun ? "매칭 다시 실행" : "매칭 실행"}
      icon={<Icon name="check" size={16} />}
      runningText="승인된 요구사항마다 경력 주장을 점수화하고 이유를 쓰는 중…"
      errors={{ APPLICATION_NOT_FOUND: "지원 건을 찾을 수 없습니다." }}
      summary={(r) =>
        `요구사항 ${Number(r.requirements ?? 0)}개 · 후보 ${Number(r.stored ?? 0)}건 · 설명 ${Number(r.explained ?? 0)}건${
          Number(r.sensitiveSkipped ?? 0) > 0
            ? ` · 기밀 주장 ${Number(r.sensitiveSkipped)}건은 제외`
            : ""
        }`
      }
      start={async () => {
        const { data, error } = await api.POST("/applications/{id}/match-jobs", {
          params: { path: { id: applicationId } },
        });
        return { jobId: data?.jobId, error: error?.detail };
      }}
    />
  );
}
