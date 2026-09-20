"use client";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Icon } from "@/components/ui/Icon";
import { api } from "@/lib/api";

/** F06: asks the worker for an AI version. The draft is never approved; the editor is where approval happens. */
export function GenerateDraftButton({
  documentId,
  hasVersion,
  hasAcceptedSources,
}: {
  documentId: string;
  hasVersion: boolean;
  hasAcceptedSources: boolean;
}) {
  return (
    <AsyncJobButton
      variant={hasVersion ? "secondary" : "primary"}
      label={hasVersion ? "AI 초안 다시 생성" : "AI 초안 생성"}
      icon={<Icon name="document" size={16} />}
      runningText="채택한 주장과 근거만으로 문장을 배열하는 중…"
      disabled={!hasAcceptedSources}
      title={
        hasAcceptedSources
          ? undefined
          : "공고 매칭에서 후보를 하나 이상 채택해야 생성할 수 있습니다."
      }
      errors={{
        DOCUMENT_NOT_FOUND: "문서를 찾을 수 없습니다.",
        TEMPLATE_NOT_FOUND: "이 문서 유형의 템플릿이 없습니다.",
        NO_ACCEPTED_SOURCES: "채택한 매칭 후보가 없습니다. 공고 매칭에서 먼저 채택하세요.",
      }}
      summary={(r) =>
        `블록 ${Number(r.blocks ?? 0)}개 생성 · 승인 필요 ${Number(r.pendingApproval ?? 0)}개${
          Array.isArray(r.dropped) && r.dropped.length > 0
            ? ` · 근거 없는 블록 ${r.dropped.length}개 제외`
            : ""
        }`
      }
      start={async () => {
        const { data, error } = await api.POST("/documents/{id}/generation-jobs", {
          params: { path: { id: documentId } },
          body: {},
        });
        return { jobId: data?.jobId, error: error?.detail };
      }}
    />
  );
}
