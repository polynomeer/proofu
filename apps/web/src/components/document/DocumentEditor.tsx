"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";

import type { Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { inputClass, textareaClass } from "@/components/ui/Field";
import { api } from "@/lib/api";
import { certaintyLabel } from "@/lib/labels";

type Block = Schema<"DocumentBlock">;
type Section = Schema<"TemplateSection">;

function CertaintyChip({ value, approved }: { value: Block["certainty"]; approved?: boolean }) {
  if (value === "SUPPORTED") return <Chip tone="verified">{certaintyLabel(value)}</Chip>;
  if (approved) return <Chip tone="neutral">{certaintyLabel(value)} · 승인됨</Chip>;
  return <Chip tone={value === "INFERRED" ? "review" : "expired"}>{certaintyLabel(value)}</Chip>;
}

function sectionOf(blockId: string): string {
  return blockId.slice(0, blockId.lastIndexOf("-"));
}

function nextBlockId(section: string, blocks: Block[]): string {
  const used = blocks
    .filter((b) => sectionOf(b.blockId) === section)
    .map((b) => Number(b.blockId.slice(section.length + 1)) || 0);
  return `${section}-${(used.length ? Math.max(...used) : 0) + 1}`;
}

/**
 * R01 canvas: blocks grouped by template section. The user edits text, drops blocks, approves
 * INFERRED/UNSUPPORTED ones and saves a new USER version. References and certainty are shown,
 * never edited here — the domain rejects anything else (GeneratedOutput.userRevision).
 */
export function DocumentEditor({
  documentId,
  parentVersionId,
  sections,
  initialBlocks,
}: {
  documentId: string;
  parentVersionId: string | null;
  sections: Section[];
  initialBlocks: Block[];
}) {
  const router = useRouter();
  const [blocks, setBlocks] = useState<Block[]>(initialBlocks);
  const [label, setLabel] = useState("");
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);

  const dirty = useMemo(
    () => JSON.stringify(blocks) !== JSON.stringify(initialBlocks),
    [blocks, initialBlocks],
  );
  const pending = blocks.filter((b) => b.certainty !== "SUPPORTED" && !b.approvedByUser).length;

  const update = (blockId: string, patch: Partial<Block>) =>
    setBlocks((all) => all.map((b) => (b.blockId === blockId ? { ...b, ...patch } : b)));
  const remove = (blockId: string) => setBlocks((all) => all.filter((b) => b.blockId !== blockId));
  const add = (section: string) =>
    setBlocks((all) => [
      ...all,
      {
        blockId: nextBlockId(section, all),
        text: "",
        claimRefs: [],
        evidenceRefs: [],
        requirementRefs: [],
        certainty: "UNSUPPORTED",
        warnings: [],
        approvedByUser: false,
      },
    ]);

  async function save() {
    setSaving(true);
    setProblem(null);
    const { data, error } = await api.POST("/documents/{id}/versions", {
      params: { path: { id: documentId } },
      body: {
        parentVersionId: parentVersionId ?? undefined,
        label: label.trim() || undefined,
        blocks: blocks.filter((b) => b.text.trim().length > 0),
      },
    });
    setSaving(false);
    if (!data) {
      setProblem(
        error?.code === "CONFLICT_STALE_VERSION"
          ? "다른 곳에서 새 버전이 저장되었습니다. 새로고침 후 다시 편집하세요."
          : error?.code === "DOMAIN_RULE_VIOLATION"
            ? "저장할 수 없는 변경입니다: 참조를 추가하거나 확신도를 올릴 수 없습니다."
            : (error?.detail ?? "저장하지 못했습니다."),
      );
      return;
    }
    setSavedAt(data.createdAt);
    setLabel("");
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border-300 bg-surface-000 px-4 py-3">
        <div className="flex flex-wrap items-center gap-3 text-caption text-text-600">
          <span>
            블록 {blocks.length}개 ·{" "}
            {pending > 0 ? (
              <span className="text-warning-700">승인 필요 {pending}개</span>
            ) : (
              <span className="text-success-700">모든 블록 내보내기 가능</span>
            )}
          </span>
          {savedAt ? <span className="text-success-700">새 버전을 저장했습니다.</span> : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input
            aria-label="버전 이름"
            placeholder="버전 이름 (선택)"
            className={`${inputClass} w-48`}
            value={label}
            maxLength={120}
            onChange={(e) => setLabel(e.target.value)}
          />
          <Button onClick={save} loading={saving} disabled={!dirty && !label.trim()}>
            <Icon name="check" size={16} />
            버전 저장
          </Button>
        </div>
      </div>
      {problem ? <ErrorState title="저장할 수 없습니다" description={problem} /> : null}

      {sections.map((section) => {
        const own = blocks.filter((b) => sectionOf(b.blockId) === section.id);
        return (
          <section
            key={section.id}
            id={`section-${section.id}`}
            className="rounded-md border border-border-300 bg-surface-000 p-4"
          >
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <div>
                <h2 className="text-card-title">{section.title}</h2>
                <p className="text-caption text-text-600">{section.guidance}</p>
              </div>
              <Button variant="tertiary" onClick={() => add(section.id)}>
                <Icon name="plus" size={16} />
                문장 추가
              </Button>
            </div>
            {own.length === 0 ? (
              <p className="text-caption text-text-600">
                이 섹션에는 아직 문장이 없습니다. 근거가 없는 문장을 직접 쓰면 “근거 없음”으로
                표시되며 내보내기 전에 승인해야 합니다.
              </p>
            ) : (
              <ul className="flex flex-col gap-3">
                {own.map((b) => (
                  <li
                    key={b.blockId}
                    className="flex flex-col gap-2 rounded-md border border-border-300 bg-surface-050 p-3"
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <CertaintyChip value={b.certainty} approved={b.approvedByUser} />
                      <span className="text-caption text-text-600">
                        주장 {b.claimRefs?.length ?? 0} · Evidence {b.evidenceRefs?.length ?? 0} ·
                        요구사항 {b.requirementRefs?.length ?? 0}
                      </span>
                      <span className="ml-auto flex items-center gap-2">
                        {b.certainty !== "SUPPORTED" ? (
                          <label className="flex items-center gap-1 text-caption">
                            <input
                              type="checkbox"
                              checked={b.approvedByUser ?? false}
                              onChange={(e) =>
                                update(b.blockId, { approvedByUser: e.target.checked })
                              }
                            />
                            내보내기 승인
                          </label>
                        ) : null}
                        <Button
                          variant="tertiary"
                          onClick={() => remove(b.blockId)}
                          aria-label={`${b.blockId} 삭제`}
                        >
                          삭제
                        </Button>
                      </span>
                    </div>
                    <textarea
                      aria-label={`${section.title} 문장 ${b.blockId}`}
                      className={textareaClass}
                      value={b.text}
                      onChange={(e) => update(b.blockId, { text: e.target.value })}
                    />
                    {(b.warnings ?? []).length > 0 ? (
                      <ul className="flex flex-col gap-1 text-caption text-warning-700">
                        {b.warnings!.map((w) => (
                          <li key={w} className="flex items-center gap-1">
                            <Icon name="alert" size={16} />
                            {w}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
