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

export const snapshotSourceLabel = labelsOf<Schema<"SnapshotSource">>({
  MANUAL_TEXT: "직접 붙여넣기",
  URL_FETCH: "URL 수집",
  CAREER_OPS: "career-ops",
});

export const applicationStatusLabel = labelsOf<Schema<"ApplicationStatus">>({
  INTERESTED: "관심",
  PREPARING: "준비",
  SUBMITTED: "제출",
  DOCUMENT_PASSED: "서류 합격",
  DOCUMENT_REJECTED: "서류 탈락",
  NO_RESPONSE: "무응답",
  WITHDRAWN: "철회",
  HANDOFF_READY: "면접 인계 준비",
  HANDED_OFF_TO_ITERVIEW: "iterview 인계됨",
  REVIEW_PENDING: "회고 대기",
  REVIEWED: "회고 완료",
});

/** Board columns (docs/domain/application-lifecycle.md §지원 보드 표시). Order is the column order. */
export const APPLICATION_BOARD_COLUMNS: readonly {
  key: string;
  label: string;
  statuses: readonly Schema<"ApplicationStatus">[];
}[] = [
  { key: "interested", label: "관심", statuses: ["INTERESTED"] },
  { key: "preparing", label: "준비", statuses: ["PREPARING"] },
  { key: "submitted", label: "제출", statuses: ["SUBMITTED"] },
  {
    key: "passed",
    label: "서류 합격",
    statuses: ["DOCUMENT_PASSED", "HANDOFF_READY", "HANDED_OFF_TO_ITERVIEW"],
  },
  {
    key: "rejected",
    label: "서류 탈락 · 무응답",
    statuses: ["DOCUMENT_REJECTED", "NO_RESPONSE", "REVIEW_PENDING", "REVIEWED"],
  },
  { key: "closed", label: "종료", statuses: ["WITHDRAWN"] },
];

export const requirementCategoryLabel = labelsOf<Schema<"RequirementCategory">>({
  REQUIRED: "필수 조건",
  PREFERRED: "우대 조건",
  RESPONSIBILITY: "책임",
  SKILL: "기술",
  BEHAVIORAL: "행동 역량",
});

export const REQUIREMENT_CATEGORIES: readonly Schema<"RequirementCategory">[] = [
  "REQUIRED",
  "PREFERRED",
  "RESPONSIBILITY",
  "SKILL",
  "BEHAVIORAL",
];

export const requirementStatusLabel = labelsOf<Schema<"RequirementStatus">>({
  DRAFT: "검토 필요",
  APPROVED: "승인됨",
  REJECTED: "제외됨",
});

export const scoreBandLabel = labelsOf<Schema<"ScoreBand">>({
  LOW: "낮음",
  MEDIUM: "보통",
  HIGH: "높음",
});

export const assessmentLabel = labelsOf<Schema<"RequirementAssessment">>({
  MET: "충족",
  PARTIALLY_MET: "부분 충족",
  UNVERIFIED: "미확인",
  UNMET: "불충족",
});

export const documentTypeLabel = labelsOf<Schema<"DocumentType">>({
  RESUME: "이력서",
  COVER_LETTER: "자기소개서",
  PORTFOLIO: "포트폴리오",
});

export const DOCUMENT_TYPES: readonly Schema<"DocumentType">[] = [
  "RESUME",
  "COVER_LETTER",
  "PORTFOLIO",
];

export const certaintyLabel = labelsOf<Schema<"Certainty">>({
  SUPPORTED: "근거 있음",
  INFERRED: "추론",
  UNSUPPORTED: "근거 없음",
});

export const versionAuthorLabel = labelsOf<"USER" | "AI">({
  USER: "사용자",
  AI: "AI 초안",
});

export const exportFormatLabel = labelsOf<Schema<"ExportFormat">>({
  DOCX: "Word (DOCX)",
  PDF: "PDF",
  MARKDOWN: "Markdown",
  JSON: "JSON",
});

export const EXPORT_FORMATS: readonly Schema<"ExportFormat">[] = [
  "DOCX",
  "PDF",
  "MARKDOWN",
  "JSON",
];

export const exportStatusLabel = labelsOf<Schema<"ExportStatus">>({
  REQUESTED: "대기",
  RENDERING: "렌더링 중",
  VALIDATING: "검증 중",
  READY: "준비됨",
  FAILED: "실패",
  EXPIRED: "만료",
});

export const atsSeverityLabel = labelsOf<Schema<"AtsSeverity">>({
  PASS: "통과",
  INFO: "참고",
  WARN: "주의",
});

export const revisionModeLabel = labelsOf<Schema<"RevisionMode">>({
  SHORTEN: "짧게",
  CLARIFY: "명확하게",
  FORMAL: "격식체로",
});

export const REVISION_MODES: readonly Schema<"RevisionMode">[] = ["SHORTEN", "CLARIFY", "FORMAL"];
