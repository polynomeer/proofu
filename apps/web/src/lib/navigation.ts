import type { IconName } from "@/components/ui/Icon";

export type NavItem = {
  href: string;
  label: string;
  icon: IconName;
};

/** Primary menu (docs/ux/information-architecture.md). Order matters for the sidebar and bottom nav. */
export const PRIMARY_NAV: readonly NavItem[] = [
  { href: "/", label: "대시보드", icon: "home" },
  { href: "/career", label: "커리어", icon: "briefcase" },
  { href: "/evidence", label: "Evidence", icon: "file-check" },
  { href: "/jobs", label: "채용공고", icon: "clipboard" },
  { href: "/documents", label: "지원 문서", icon: "document" },
  { href: "/applications", label: "지원 관리", icon: "kanban" },
];

export const SECONDARY_NAV: readonly NavItem[] = [
  { href: "/settings", label: "설정", icon: "settings" },
  { href: "/help", label: "도움말", icon: "help" },
];

/** Mobile shows the first four primary items; the rest live under "더보기". */
export const MOBILE_NAV_COUNT = 4;

export function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}
