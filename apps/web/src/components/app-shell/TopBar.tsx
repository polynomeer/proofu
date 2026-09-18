import { Icon } from "@/components/ui/Icon";

/** Global search, notifications and user menu (docs/design/components.md §상단 바). */
export function TopBar({ userName }: { userName: string }) {
  return (
    <header className="sticky top-0 z-10 flex h-16 items-center gap-4 border-b border-border-300 bg-surface-000 px-4 md:px-6">
      <span className="text-[22px] font-bold text-primary-600 md:hidden">ProofU</span>
      <form role="search" className="relative flex-1 md:max-w-xl" action="/search">
        <label htmlFor="global-search" className="sr-only">
          통합 검색
        </label>
        <Icon
          name="search"
          size={16}
          className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-text-600"
        />
        <input
          id="global-search"
          name="q"
          type="search"
          placeholder="경력, 스킬, 회사, 키워드 등을 검색하세요"
          className="h-10 w-full rounded-md border border-border-300 bg-surface-000 pr-3 pl-9 text-body placeholder:text-text-600"
        />
      </form>
      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          className="flex h-10 w-10 items-center justify-center rounded-md text-text-600 hover:bg-surface-050"
          aria-label="알림"
          title="알림"
        >
          <Icon name="bell" />
        </button>
        <button
          type="button"
          className="flex h-10 items-center gap-2 rounded-md px-2 text-body hover:bg-surface-050"
          aria-haspopup="menu"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-100 text-text-600">
            <Icon name="user" size={16} />
          </span>
          <span className="hidden sm:inline">{userName}</span>
          <Icon name="chevron-down" size={16} className="text-text-600" />
        </button>
      </div>
    </header>
  );
}
