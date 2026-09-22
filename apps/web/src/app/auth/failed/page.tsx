import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "로그인 실패" };

const REASONS: Record<string, string> = {
  provider: "로그인 제공자가 요청을 거부했습니다.",
  state: "로그인 요청이 만료되었거나 위조되었습니다. 다시 시도하세요.",
  tokens: "로그인 제공자가 필요한 토큰을 주지 않았습니다.",
  email:
    "이메일이 아직 확인되지 않았습니다. 받은 편지함의 확인 메일을 처리한 뒤 다시 로그인하세요.",
  exchange: "로그인 제공자와 통신하지 못했습니다. 잠시 후 다시 시도하세요.",
};

export default async function FailedPage({
  searchParams,
}: {
  searchParams: Promise<{ reason?: string }>;
}) {
  const { reason } = await searchParams;
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-4 px-4">
      <h1 className="text-page-title">로그인하지 못했습니다</h1>
      <p className="text-body text-text-600">{REASONS[reason ?? ""] ?? "알 수 없는 오류입니다."}</p>
      <Link href="/auth/login" className="text-primary-600 hover:underline">
        다시 로그인
      </Link>
    </main>
  );
}
