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

export const evidenceTypeLabel = labelsOf<Schema<"EvidenceType">>({
  FILE: "파일",
  URL: "링크",
  REPOSITORY: "저장소",
  COMMIT: "커밋",
  METRIC: "지표",
  CERTIFICATE: "인증서",
  NOTE: "메모",
  REFERENCE_LETTER: "추천서",
});

/** FILE is excluded until object storage is decided (docs/project/open-decisions.md). */
export const EVIDENCE_TYPES: readonly Schema<"EvidenceType">[] = [
  "URL",
  "REPOSITORY",
  "COMMIT",
  "METRIC",
  "CERTIFICATE",
  "REFERENCE_LETTER",
  "NOTE",
];

export const verificationLabel = labelsOf<Schema<"VerificationStatus">>({
  UNVERIFIED: "검토 필요",
  USER_VERIFIED: "검증됨",
  EXTERNALLY_VERIFIED: "외부 검증",
  EXPIRED: "만료",
});

export const sensitivityLabel = labelsOf<Schema<"Sensitivity">>({
  PUBLIC: "공개",
  INTERNAL: "내부",
  CONFIDENTIAL: "기밀",
  RESTRICTED: "제한",
});

export const SENSITIVITIES: readonly Schema<"Sensitivity">[] = [
  "PUBLIC",
  "INTERNAL",
  "CONFIDENTIAL",
  "RESTRICTED",
];

export const claimTypeLabel = labelsOf<Schema<"ClaimType">>({
  FACT: "사실",
  INFERENCE: "추론",
  OPINION: "의견",
});

export const CLAIM_TYPES: readonly Schema<"ClaimType">[] = ["FACT", "INFERENCE", "OPINION"];

export const claimStatusLabel = labelsOf<Schema<"ClaimStatus">>({
  UNSUPPORTED: "근거 없음",
  SUPPORTED: "근거 있음",
  CONTESTED: "반박 있음",
});

export const relationLabel = labelsOf<Schema<"EvidenceRelation">>({
  SUPPORTS: "지지",
  REFUTES: "반박",
  PARTIALLY_SUPPORTS: "부분 지지",
});

export const RELATIONS: readonly Schema<"EvidenceRelation">[] = [
  "SUPPORTS",
  "PARTIALLY_SUPPORTS",
  "REFUTES",
];
