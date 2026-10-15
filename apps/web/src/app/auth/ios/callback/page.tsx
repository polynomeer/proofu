import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "앱에서 로그인", robots: { index: false } };

/**
 * The iOS app's OIDC redirect (ADR-0011 §3). With the app installed iOS opens it instead of this
 * page; landing here means it is not, so the authorization code in the URL is left unused — the
 * web never redeems a code issued to another client.
 */
export default function IosCallbackPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-4 px-4">
      <h1 className="text-page-title">ProofU 앱에서 로그인을 마쳐 주세요</h1>
      <p className="text-body text-text-600">
        이 주소는 iPhone 앱이 로그인을 이어받는 곳입니다. 앱이 설치되어 있지 않아 브라우저가
        열었습니다.
      </p>
      <Link href="/auth/login" className="text-primary-600 underline">
        웹에서 로그인
      </Link>
    </main>
  );
}
