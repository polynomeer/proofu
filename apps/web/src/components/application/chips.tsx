import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { applicationStatusLabel } from "@/lib/labels";

type Status = Schema<"ApplicationStatus">;

const TONE: Partial<Record<Status, "verified" | "review" | "expired" | "snapshot" | "private">> = {
  SUBMITTED: "snapshot",
  DOCUMENT_PASSED: "verified",
  HANDOFF_READY: "verified",
  HANDED_OFF_TO_ITERVIEW: "verified",
  DOCUMENT_REJECTED: "expired",
  NO_RESPONSE: "expired",
  REVIEW_PENDING: "review",
  WITHDRAWN: "private",
};

export function ApplicationStatusChip({ value }: { value: Status }) {
  return <Chip tone={TONE[value] ?? "neutral"}>{applicationStatusLabel(value)}</Chip>;
}
