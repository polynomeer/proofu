import Link from "next/link";

/**
 * Unknown paths land outside the app shell (no sidebar, no session needed), so this page
 * stands on its own and points back to the dashboard.
 */
export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-prose flex-col justify-center gap-4 px-6">
      <p className="text-caption text-text-600">404</p>
      <h1 className="text-section-title">페이지를 찾을 수 없습니다</h1>
      <p className="text-body text-text-600">
        주소가 바뀌었거나 삭제된 화면일 수 있습니다. 대시보드에서 다시 시작하세요.
      </p>
      <Link
        href="/"
        className="inline-flex h-10 w-fit items-center rounded-md bg-primary-600 px-4 text-body font-semibold text-white hover:bg-primary-700"
      >
        대시보드로
      </Link>
    </main>
  );
}
