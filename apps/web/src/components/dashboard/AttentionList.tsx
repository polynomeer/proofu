import Link from "next/link";

import type { Schema } from "@proofu/contracts";

import { Icon } from "@/components/ui/Icon";

type Attention = Schema<"DashboardSummary">["attention"][number];

/** Server codes → one concrete next action each. Unknown codes are skipped rather than mislabelled. */
const ACTIONS: Record<string, (count: number) => { text: string; href: string; cta: string }> = {
  NO_CAREER_ENTRIES: () => ({
    text: "아직 경력이 없습니다. 첫 경력을 등록하면 타임라인이 시작됩니다.",
    href: "/career/new",
    cta: "새 경력 추가",
  }),
  UNSUPPORTED_CLAIMS: (n) => ({
    text: `근거가 연결되지 않은 주장이 ${n}개 있습니다. Evidence를 연결하기 전에는 문서에 자동으로 쓰이지 않습니다.`,
    href: "/career",
    cta: "주장 확인",
  }),
  UNVERIFIED_EVIDENCE: (n) => ({
    text: `검토가 필요한 Evidence가 ${n}개 있습니다. 내용을 확인하고 검증 상태를 올려 주세요.`,
    href: "/evidence?verification=UNVERIFIED",
    cta: "Evidence 검토",
  }),
  ENTRIES_WITHOUT_PROJECTS: (n) => ({
    text: `프로젝트가 없는 근무 경력이 ${n}개 있습니다. 프로젝트와 성과를 추가하면 지원 문서의 재료가 됩니다.`,
    href: "/career?type=EMPLOYMENT",
    cta: "경력 보기",
  }),
};

export function AttentionList({ items }: { items: Attention[] }) {
  const actions = items.map((i) => ACTIONS[i.code]?.(i.count)).filter(Boolean);
  if (actions.length === 0) return null;
  return (
    <section aria-label="다음 행동" className="mb-6 flex flex-col gap-2">
      {actions.map((a) => (
        <div
          key={a!.href + a!.text}
          className="flex flex-wrap items-center gap-3 rounded-md border border-warning-600/40 bg-warning-050 px-4 py-3 text-body"
        >
          <Icon name="alert" size={16} className="shrink-0 text-warning-700" />
          <span className="min-w-0 flex-1">{a!.text}</span>
          <Link href={a!.href} className="font-semibold text-primary-600 hover:underline">
            {a!.cta} →
          </Link>
        </div>
      ))}
    </section>
  );
}
