import type { ReactNode } from "react";

import { Icon, type IconName } from "@/components/ui/Icon";

type Tone = "verified" | "review" | "expired" | "private" | "snapshot" | "neutral";

/** Status chips always pair color with text and an icon (docs/design/components.md §상태 칩). */
const TONE: Record<Tone, { className: string; icon?: IconName }> = {
  verified: { className: "bg-success-050 text-success-700", icon: "check" },
  review: { className: "bg-warning-050 text-warning-700", icon: "alert" },
  expired: { className: "bg-neutral-100 text-neutral-700", icon: "clock" },
  private: { className: "bg-neutral-100 text-neutral-700", icon: "lock" },
  snapshot: { className: "bg-primary-050 text-primary-700", icon: "lock" },
  neutral: { className: "bg-surface-050 text-text-600" },
};

export function Chip({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  const { className, icon } = TONE[tone];
  return (
    <span
      className={`inline-flex h-6 items-center gap-1 rounded-sm px-2 text-caption font-medium ${className}`}
    >
      {icon ? <Icon name={icon} size={16} /> : null}
      {children}
    </span>
  );
}
