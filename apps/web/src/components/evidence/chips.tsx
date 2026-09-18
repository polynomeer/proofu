import type { Schema } from "@proofu/contracts";

import { Chip } from "@/components/ui/Chip";
import { claimStatusLabel, sensitivityLabel, verificationLabel } from "@/lib/labels";

export function VerificationChip({ value }: { value: Schema<"VerificationStatus"> }) {
  const tone =
    value === "USER_VERIFIED" || value === "EXTERNALLY_VERIFIED"
      ? "verified"
      : value === "EXPIRED"
        ? "expired"
        : "review";
  return <Chip tone={tone}>{verificationLabel(value)}</Chip>;
}

export function SensitivityChip({ value }: { value: Schema<"Sensitivity"> }) {
  const tone = value === "CONFIDENTIAL" || value === "RESTRICTED" ? "private" : "neutral";
  return <Chip tone={tone}>{sensitivityLabel(value)}</Chip>;
}

export function ClaimStatusChip({ value }: { value: Schema<"ClaimStatus"> }) {
  const tone = value === "SUPPORTED" ? "verified" : value === "CONTESTED" ? "expired" : "review";
  return <Chip tone={tone}>{claimStatusLabel(value)}</Chip>;
}
