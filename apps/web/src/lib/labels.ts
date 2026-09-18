import type { Schema } from "@proofu/contracts";

/**
 * Server enum → UI label maps. Unknown values fall back to the raw code so a newer
 * server never renders an empty label (docs/design/components.md §프론트엔드 계약).
 */
function labelsOf<T extends string>(map: Record<T, string>) {
  return (value: string): string => (value in map ? map[value as T] : value);
}

export const careerEntryTypeLabel = labelsOf<Schema<"CareerEntryType">>({
  EMPLOYMENT: "근무 경력",
  EDUCATION: "학력",
  TRAINING: "교육",
  AWARD: "수상",
  CERTIFICATION: "자격증",
  OTHER: "기타",
});

export const visibilityLabel = labelsOf<Schema<"Visibility">>({
  PRIVATE: "비공개",
  SELECTIVE: "선택 공개",
  PUBLIC: "전체 공개",
});

export const CAREER_ENTRY_TYPES: readonly Schema<"CareerEntryType">[] = [
  "EMPLOYMENT",
  "EDUCATION",
  "TRAINING",
  "AWARD",
  "CERTIFICATION",
  "OTHER",
];

export const VISIBILITIES: readonly Schema<"Visibility">[] = ["PRIVATE", "SELECTIVE", "PUBLIC"];
