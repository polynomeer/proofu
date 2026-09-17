import { API_BASE_PATH } from "@proofu/contracts";

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-content flex-col items-start justify-center gap-6 px-6 py-8">
      <p className="text-page-title text-primary-600">ProofU</p>
      <h1 className="text-section-title">당신의 경험이, 더 큰 기회가 되는 곳</h1>
      <p className="max-w-prose text-body text-text-600">
        경력 사실과 증빙을 축적하고, 채용공고 요구사항에 맞춰 근거를 추적할 수 있는 지원 문서를
        만듭니다. 이 화면은 프로젝트 기반 세팅 확인용 자리표시자입니다.
      </p>
      <dl className="grid grid-cols-1 gap-4 rounded-md border border-border-300 bg-surface-000 p-4 text-caption text-text-600 sm:grid-cols-3">
        <div>
          <dt className="font-semibold text-text-900">API</dt>
          <dd>{API_BASE_PATH}</dd>
        </div>
        <div>
          <dt className="font-semibold text-text-900">디자인 토큰</dt>
          <dd>src/styles/tokens.css</dd>
        </div>
        <div>
          <dt className="font-semibold text-text-900">계약</dt>
          <dd>packages/contracts/openapi.yaml</dd>
        </div>
      </dl>
    </main>
  );
}
