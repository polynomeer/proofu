import type { Metadata } from "next";
import Link from "next/link";

import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { currentCaller } from "@/lib/auth/current";
import { MOBILE_NAV_COUNT, PRIMARY_NAV, SECONDARY_NAV } from "@/lib/navigation";

export const metadata: Metadata = { title: "더보기" };

/**
 * The mobile bottom bar holds four destinations; everything else lives here, including the
 * sign-out that the desktop top bar carries (docs/ux/information-architecture.md).
 */
export default async function MorePage() {
  const caller = await currentCaller();
  const rest = [...PRIMARY_NAV.slice(MOBILE_NAV_COUNT), ...SECONDARY_NAV];

  return (
    <>
      <PageHeader title="더보기" description={caller?.name ?? undefined} />
      <nav aria-label="나머지 메뉴">
        <ul className="flex flex-col divide-y divide-border-300 rounded-md border border-border-300 bg-surface-000">
          {rest.map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className="flex h-14 items-center gap-3 px-4 text-body hover:bg-surface-050"
              >
                <Icon name={item.icon} />
                {item.label}
                <Icon name="chevron-right" size={16} className="ml-auto text-text-600" />
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      {caller?.mode === "oidc" ? (
        <form action="/auth/logout" method="post" className="mt-4">
          <button
            type="submit"
            className="h-12 w-full rounded-md border border-border-300 bg-surface-000 text-body font-semibold hover:bg-surface-050"
          >
            로그아웃
          </button>
        </form>
      ) : null}
    </>
  );
}
