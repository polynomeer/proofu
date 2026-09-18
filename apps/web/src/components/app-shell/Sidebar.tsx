"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { Icon } from "@/components/ui/Icon";
import { PRIMARY_NAV, SECONDARY_NAV, isActive, type NavItem } from "@/lib/navigation";

function NavLink({ item, active }: { item: NavItem; active: boolean }) {
  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={[
        "flex h-10 items-center gap-3 rounded-md px-3 text-body text-white/80 transition-colors",
        "hover:bg-white/10 hover:text-white",
        active ? "bg-primary-600 font-semibold text-white" : "",
      ].join(" ")}
    >
      <Icon name={item.icon} />
      <span>{item.label}</span>
    </Link>
  );
}

/** Fixed 224px navigation on ≥768px (docs/design/components.md §사이드바). */
export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="hidden w-sidebar shrink-0 flex-col bg-primary-800 text-white md:flex">
      <div className="flex h-16 items-center px-5">
        <Link href="/" className="text-[26px] font-bold tracking-tight" aria-label="ProofU 홈">
          ProofU
        </Link>
      </div>
      <nav aria-label="주 메뉴" className="flex flex-1 flex-col gap-1 px-3 pt-2">
        {PRIMARY_NAV.map((item) => (
          <NavLink key={item.href} item={item} active={isActive(pathname, item.href)} />
        ))}
      </nav>
      <nav
        aria-label="보조 메뉴"
        className="flex flex-col gap-1 border-t border-white/10 px-3 py-3"
      >
        {SECONDARY_NAV.map((item) => (
          <NavLink key={item.href} item={item} active={isActive(pathname, item.href)} />
        ))}
      </nav>
    </aside>
  );
}
