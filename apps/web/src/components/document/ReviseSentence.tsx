"use client";

import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { AsyncJobButton } from "@/components/jobs/AsyncJobButton";
import { Button } from "@/components/ui/Button";
import { inputClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { revisionModeLabel, REVISION_MODES } from "@/lib/labels";

type Mode = Schema<"RevisionMode">;

type Proposal = {
  original: string;
  revised: string | null;
  changes: string[];
  rejected: string[];
};

/**
 * "문장 개선": ask for one rewrite, compare it with the current text and apply it into the
 * editor. Applying only changes the text; references and certainty stay, and nothing is saved
 * until the user saves a version.
 */
export function ReviseSentence({
  documentId,
  versionId,
  blockId,
  currentText,
  onApply,
}: {
  documentId: string;
  versionId: string | null;
  blockId: string;
  currentText: string;
  onApply: (text: string) => void;
}) {
  const [mode, setMode] = useState<Mode>("CLARIFY");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const savedInVersion = versionId !== null;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label={`${blockId} 개선 모드`}
          className={`${inputClass} h-8 w-32 text-caption`}
          value={mode}
          onChange={(e) => setMode(e.target.value as Mode)}
        >
          {REVISION_MODES.map((m) => (
            <option key={m} value={m}>
              {revisionModeLabel(m)}
            </option>
          ))}
        </select>
        <AsyncJobButton
          variant="tertiary"
          className="h-8 px-2 text-caption"
          label="AI 개선 제안"
          runningText="문장과 인용한 사실만 보고 다시 쓰는 중…"
          disabled={!savedInVersion}
          title={
            savedInVersion
              ? undefined
              : "저장된 버전의 문장만 개선할 수 있습니다. 먼저 버전을 저장하세요."
          }
          refreshOnDone={false}
          errors={{
            BLOCK_NOT_FOUND: "저장된 버전에 이 문장이 없습니다. 먼저 버전을 저장하세요.",
            VERSION_NOT_FOUND: "문서 버전을 찾을 수 없습니다.",
          }}
          summary={(r) =>
            r.revised
              ? "제안이 도착했습니다. 아래에서 비교하세요."
              : "제안이 검사를 통과하지 못했습니다."
          }
          onDone={(r) =>
            setProposal({
              original: String(r.original ?? ""),
              revised: typeof r.revised === "string" ? r.revised : null,
              changes: Array.isArray(r.changes) ? r.changes.map(String) : [],
              rejected: Array.isArray(r.rejected) ? r.rejected.map(String) : [],
            })
          }
          start={async () => {
            if (!versionId) return { error: "저장된 버전이 없습니다." };
            const { data, error } = await api.POST("/documents/{id}/revision-jobs", {
              params: { path: { id: documentId } },
              body: { versionId, blockId, mode },
            });
            return { jobId: data?.jobId, error: error?.detail };
          }}
        />
      </div>
      {proposal ? (
        <div className="flex flex-col gap-2 rounded-md border border-primary-600/40 bg-surface-000 p-3 text-caption">
          {proposal.revised === null ? (
            <p className="text-warning-700">
              제안을 버렸습니다: 사실의 의미가 바뀔 수 있는 변경입니다 (
              {proposal.rejected.join("; ")}).
            </p>
          ) : (
            <>
              <div className="grid gap-2 sm:grid-cols-2">
                <div>
                  <span className="font-semibold text-text-600">현재</span>
                  <p className="mt-1 whitespace-pre-line text-body">{currentText}</p>
                </div>
                <div>
                  <span className="font-semibold text-primary-700">제안</span>
                  <p className="mt-1 whitespace-pre-line text-body">{proposal.revised}</p>
                </div>
              </div>
              {proposal.changes.length > 0 ? (
                <ul className="flex flex-col gap-0.5 text-text-600">
                  {proposal.changes.map((c) => (
                    <li key={c}>· {c}</li>
                  ))}
                </ul>
              ) : null}
              {proposal.original !== currentText ? (
                <p className="text-warning-700">
                  제안은 저장된 버전의 문장을 기준으로 만들어졌습니다. 현재 편집 중인 문장과 다를 수
                  있습니다.
                </p>
              ) : null}
              <div className="flex gap-2">
                <Button
                  className="h-8 px-3 text-caption"
                  onClick={() => {
                    onApply(proposal.revised!);
                    setProposal(null);
                  }}
                >
                  제안 적용
                </Button>
                <Button
                  variant="tertiary"
                  className="h-8 px-2 text-caption"
                  onClick={() => setProposal(null)}
                >
                  무시
                </Button>
              </div>
            </>
          )}
        </div>
      ) : null}
    </div>
  );
}
