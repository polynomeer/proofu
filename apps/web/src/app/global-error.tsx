"use client";

/**
 * Last resort: the app shell itself failed, so this renders its own document. Plain markup
 * only — the shell's styles and components may be exactly what is broken.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="ko">
      <body
        style={{
          fontFamily: "system-ui, sans-serif",
          margin: 0,
          padding: "3rem 1.5rem",
          color: "#12161C",
          background: "#F5F7FA",
        }}
      >
        <main style={{ maxWidth: "36rem" }}>
          <h1 style={{ fontSize: "1.5rem", marginBottom: "0.5rem" }}>ProofU를 열지 못했습니다</h1>
          <p style={{ lineHeight: 1.6 }}>
            일시적인 문제일 수 있습니다. 다시 시도해도 같은 화면이 나오면 잠시 후 다시 열어 주세요.
            저장된 데이터는 그대로입니다.
          </p>
          {error.digest ? (
            <p style={{ color: "#5B6472" }}>
              문의 코드: <code>{error.digest}</code>
            </p>
          ) : null}
          <button
            type="button"
            onClick={reset}
            style={{
              marginTop: "1.5rem",
              padding: "0.625rem 1rem",
              borderRadius: "0.375rem",
              border: 0,
              background: "#3B5998",
              color: "white",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            다시 시도
          </button>
        </main>
      </body>
    </html>
  );
}
