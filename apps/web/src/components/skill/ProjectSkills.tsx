"use client";

import Link from "next/link";
import { useState } from "react";

import type { Schema } from "@proofu/contracts";

import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { api } from "@/lib/api";
import { PICKER_LIMIT } from "@/lib/limits";
import { proficiencyLabel, skillCategoryLabel } from "@/lib/labels";

type Skill = Schema<"Skill">;

/**
 * Which registered skills this project used. The set is replaced as a whole
 * (`PUT /projects/{id}/skills`); skills themselves are managed under 커리어 → 기술.
 */
export function ProjectSkills({
  projectId,
  available,
  initialItems,
}: {
  projectId: string;
  available: Skill[];
  initialItems: Skill[];
}) {
  const [items, setItems] = useState(initialItems);
  const [editing, setEditing] = useState(false);
  const [selected, setSelected] = useState(() => new Set(initialItems.map((s) => s.id)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    const { data, error: problem } = await api.PUT("/projects/{id}/skills", {
      params: { path: { id: projectId } },
      body: { skillIds: [...selected] },
    });
    setBusy(false);
    if (!data) {
      setError(problem?.detail ?? "저장하지 못했습니다.");
      return;
    }
    setItems(data.items);
    setEditing(false);
  }

  return (
    <section className="rounded-md border border-border-300 bg-surface-000 p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-section-title">사용 기술</h2>
        {!editing ? (
          <Button
            variant="tertiary"
            className="h-8 px-2 text-caption"
            onClick={() => {
              setSelected(new Set(items.map((s) => s.id)));
              setEditing(true);
            }}
          >
            {items.length > 0 ? "편집" : "연결"}
          </Button>
        ) : null}
      </div>

      {error ? (
        <p role="alert" className="mt-2 text-caption text-warning-700">
          {error}
        </p>
      ) : null}

      {editing ? (
        available.length === 0 ? (
          <p className="mt-3 text-caption text-text-600">
            등록된 기술이 없습니다.{" "}
            <Link
              href="/career/skills"
              className="text-primary-600 underline-offset-2 hover:underline"
            >
              기술 목록
            </Link>
            에서 먼저 추가하세요.
          </p>
        ) : (
          <>
            {available.length >= PICKER_LIMIT ? (
              <p className="mt-3 text-caption text-text-600">
                최근 기술 {available.length}개만 보여 줍니다. 찾는 기술이 없으면 커리어 → 기술에서
                확인하세요.
              </p>
            ) : null}
            <ul className="mt-3 flex flex-col gap-1">
              {available.map((s) => (
                <li key={s.id}>
                  <label htmlFor={`ps-${s.id}`} className="flex items-center gap-2 text-body">
                    <input
                      id={`ps-${s.id}`}
                      type="checkbox"
                      aria-label={s.canonicalName}
                      checked={selected.has(s.id)}
                      onChange={(e) =>
                        setSelected((prev) => {
                          const next = new Set(prev);
                          if (e.target.checked) next.add(s.id);
                          else next.delete(s.id);
                          return next;
                        })
                      }
                    />
                    <span>{s.canonicalName}</span>
                    <span className="text-caption text-text-600">
                      {skillCategoryLabel(s.category)}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex gap-2">
              <Button onClick={save} loading={busy}>
                저장
              </Button>
              <Button variant="tertiary" onClick={() => setEditing(false)}>
                취소
              </Button>
            </div>
          </>
        )
      ) : items.length === 0 ? (
        <p className="mt-2 text-caption text-text-600">
          아직 연결한 기술이 없습니다. 프로젝트에서 쓴 언어·도구를 연결해 두면 공고 요구사항과
          대조할 때 참고할 수 있습니다.
        </p>
      ) : (
        <ul className="mt-3 flex flex-wrap gap-2">
          {items.map((s) => (
            <li key={s.id}>
              <Chip>
                {s.canonicalName}
                {s.proficiency ? ` · ${proficiencyLabel(s.proficiency)}` : ""}
              </Chip>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
