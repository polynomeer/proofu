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
  DEADLINES_SOON: (n) => ({
    text: `7일 안에 마감되는 지원이 ${n}건 있습니다. 문서를 마무리하고 제출 스냅샷을 남기세요.`,
    href: "/applications",
    cta: "지원 관리",
  }),
  REVIEWS_PENDING: (n) => ({
    text: `서류 결과가 나왔지만 회고를 쓰지 않은 지원이 ${n}건 있습니다. 사실과 가설을 분리해 남기세요.`,
    href: "/applications",
    cta: "회고 쓰기",
  }),
  DRAFT_REQUIREMENTS: (n) => ({
    text: `AI가 뽑은 요구사항 초안 ${n}개가 승인을 기다립니다. 승인된 요구사항만 매칭과 문서에 쓰입니다.`,
    href: "/jobs",
    cta: "요구사항 검토",
  }),
  MATCH_NOT_RUN: (n) => ({
    text: `승인된 요구사항이 있지만 매칭을 실행하지 않은 지원이 ${n}건 있습니다. 매칭 후 후보를 채택해야 초안을 만들 수 있습니다.`,
    href: "/applications",
    cta: "매칭 실행",
  }),
  BLOCKS_PENDING_APPROVAL: (n) => ({
    text: `승인되지 않은 근거 없음·추론 문장이 남은 문서가 ${n}개 있습니다. 승인 전에는 내보내거나 제출할 수 없습니다.`,
    href: "/documents",
    cta: "문서 확인",
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
