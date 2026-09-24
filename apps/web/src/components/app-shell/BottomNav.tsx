"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { Icon } from "@/components/ui/Icon";
import { MOBILE_NAV_COUNT, PRIMARY_NAV, isActive } from "@/lib/navigation";

/** Mobile navigation: four primary items plus "더보기" (docs/design/design-tokens.md §브레이크포인트). */
export function BottomNav() {
  const pathname = usePathname();
  const items = PRIMARY_NAV.slice(0, MOBILE_NAV_COUNT);
  return (
    <nav
      aria-label="주 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-border-300 bg-surface-000 md:hidden"
    >
      {items.map((item) => {
        const active = isActive(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={[
              "flex h-14 flex-col items-center justify-center gap-1 text-caption",
              active ? "font-semibold text-primary-600" : "text-text-600",
            ].join(" ")}
          >
            <Icon name={item.icon} />
            {item.label}
          </Link>
        );
      })}
      <Link
        href="/more"
        aria-current={isActive(pathname, "/more") ? "page" : undefined}
        className={[
          "flex h-14 flex-col items-center justify-center gap-1 text-caption",
          isActive(pathname, "/more") ? "font-semibold text-primary-600" : "text-text-600",
        ].join(" ")}
      >
        <Icon name="more" />
        더보기
      </Link>
    </nav>
  );
}
