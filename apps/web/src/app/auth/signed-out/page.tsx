import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "로그아웃" };

export default function SignedOutPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-4 px-4">
      <h1 className="text-page-title">로그아웃했습니다</h1>
      <p className="text-body text-text-600">이 브라우저의 세션을 종료했습니다.</p>
      <Link href="/auth/login" className="text-primary-600 hover:underline">
        다시 로그인
      </Link>
    </main>
  );
}
