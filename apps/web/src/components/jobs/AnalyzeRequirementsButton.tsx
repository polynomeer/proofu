"use client";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

/** F04 entry point: drafts land in the review panel as 검토 필요 after the job finishes. */
export function AnalyzeRequirementsButton({ snapshotId }: { snapshotId?: string }) {
  return (
    <AsyncJobButton
      label="요구사항 분석"
      icon={<Icon name="clipboard" size={16} />}
      disabled={!snapshotId}
      title={
        !snapshotId
          ? "저장된 본문이 필요합니다"
          : "AI가 원문에서 요구사항 초안을 뽑아 검토 목록에 넣습니다"
      }
      runningText="분석 중… 결과는 검토 필요 상태로 들어옵니다."
      errors={{ SNAPSHOT_NOT_FOUND: "스냅샷을 찾을 수 없습니다." }}
      summary={(r) => {
        const extracted = Number(r.extracted ?? 0);
        const withoutSpan = Number(r.withoutSpan ?? 0);
        return `초안 ${extracted}개 추가됨${withoutSpan > 0 ? ` (원문 위치 미확인 ${withoutSpan}개)` : ""}. 아래에서 승인하세요.`;
      }}
      start={async () => {
        if (!snapshotId) return { error: "저장된 본문이 필요합니다." };
        const { data, error } = await api.POST("/job-posting-snapshots/{id}/analysis-jobs", {
          params: { path: { id: snapshotId } },
        });
        return { jobId: data?.jobId, error: error?.detail };
      }}
    />
  );
}
