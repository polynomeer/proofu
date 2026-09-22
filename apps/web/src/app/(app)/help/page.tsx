import type { Metadata } from "next";
import Link from "next/link";

import { Chip } from "@/components/ui/Chip";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = { title: "도움말" };

/** Each step names the screen it happens on; wording mirrors docs/product/user-journeys.md. */
const STEPS: { title: string; body: string; href: string; label: string }[] = [
  {
    title: "경력을 사실 단위로 기록",
    body: "경력·학력·자격을 등록하고 프로젝트와 성과를 붙입니다. 성과는 행동이 아니라 결과(수치·기간)로 씁니다.",
    href: "/career",
    label: "커리어",
  },
  {
    title: "주장에 Evidence 연결",
    body: "문서에서 말하고 싶은 문장(Claim)을 경력 기록에 대해 만들고, 링크·메모 등 Evidence를 연결합니다. 검증 상태는 사용자만 올릴 수 있습니다.",
    href: "/evidence",
    label: "Evidence",
  },
  {
    title: "채용공고 저장과 요구사항 승인",
    body: "공고 원문을 붙여 넣으면 불변 스냅샷이 남습니다. AI가 뽑은 요구사항 초안은 승인해야 쓰이고, 직접 입력한 요구사항은 바로 승인됩니다.",
    href: "/jobs",
    label: "채용공고",
  },
  {
    title: "지원 만들기와 매칭",
    body: "공고에서 지원 건을 만들고 ‘공고 매칭’을 실행하면 승인된 요구사항마다 관련 주장이 점수와 함께 나열됩니다. 점수는 정렬 기준이지 합격 가능성이 아닙니다. 쓸 후보를 채택하세요.",
    href: "/applications",
    label: "지원 관리",
  },
  {
    title: "문서 초안, 승인, 버전",
    body: "채택한 주장만으로 AI가 문장을 배열합니다. 근거 없음·추론 문장은 승인해야 내보낼 수 있고, 저장할 때마다 버전과 출처(provenance)가 남습니다.",
    href: "/documents",
    label: "지원 문서",
  },
  {
    title: "내보내기와 제출 스냅샷",
    body: "DOCX·PDF·Markdown·JSON으로 내보내고 ATS 검사로 구조를 확인합니다. 제출하면 문서 버전과 공고 스냅샷이 불변으로 고정됩니다.",
    href: "/applications",
    label: "지원 관리",
  },
  {
    title: "결과 기록과 회고",
    body: "서류 탈락·무응답이면 관찰한 사실과 가설을 분리해 회고합니다. 비교 대상(제출 문서 사실, 매칭 판정, ATS 결과)을 근거로 인용할 수 있습니다.",
    href: "/applications",
    label: "지원 관리",
  },
];

const TERMS: { term: string; definition: string }[] = [
  {
    term: "Claim (주장)",
    definition:
      "문서에서 주장하려는 사실. 경력 기록에 연결되며 상태는 Evidence 연결로 결정됩니다: 근거 없음 · 근거 있음 · 반박됨.",
  },
  {
    term: "Evidence",
    definition:
      "주장을 뒷받침하는 링크, 메모, 지표, 자격증 등. 검증 상태(미검증·사용자 검증·외부 검증·만료)는 사용자가 정합니다.",
  },
  {
    term: "Requirement (요구사항)",
    definition:
      "공고에서 추출한 필수·우대·업무·기술·행동 조건. 승인된 것만 매칭과 문서 생성에 쓰입니다.",
  },
  {
    term: "확신도 (certainty)",
    definition:
      "문서 문장의 근거 수준. 근거 있음(인용한 주장이 모두 근거 있음) · 추론 · 근거 없음. 모델이 낮출 수는 있어도 올릴 수는 없습니다.",
  },
  {
    term: "Provenance (출처)",
    definition:
      "문서 문장이 어느 주장·Evidence·요구사항의 어느 revision에서 나왔는지. 생성 후 바뀌지 않습니다.",
  },
  {
    term: "제출 스냅샷",
    definition: "제출한 문서 버전과 공고 스냅샷의 불변 묶음. 정정은 새 스냅샷으로만 합니다.",
  },
  {
    term: "회고",
    definition:
      "관찰 사실·가설·근거·개선 행동·검증 계획. 탈락 원인을 단정하는 표현은 경고만 합니다.",
  },
];

const PRINCIPLES: string[] = [
  "AI는 기록에 없는 성과·수치·기술을 만들지 않습니다. 모델이 돌려준 참조는 서버가 화이트리스트로 다시 검증합니다.",
  "AI는 Evidence 검증 상태를 올리지 못하고, 점수를 매기지 않습니다(매칭 점수는 결정적 계산).",
  "기밀·제한 민감도 데이터는 동의 없이 AI에 전송되지 않습니다. 프로필 연락처는 어떤 경우에도 전송되지 않습니다.",
  "합격 가능성은 어디에도 표시하지 않습니다. 점수는 낮음/보통/높음, ATS 검사는 항목별 통과/참고/주의뿐입니다.",
  "AI 결과는 항상 초안(미승인)으로 저장되고 승인은 같은 화면에서 사용자가 합니다.",
];

const PENDING: string[] = [
  "파일 Evidence 업로드 (객체 저장소 확정 후)",
  "URL로 공고 가져오기 (지금은 원문 붙여넣기)",
  "iterview 면접 인계",
  "공개 범위 기본값, AI 처리 동의, 데이터 삭제",
];

export default function HelpPage() {
  return (
    <>
      <PageHeader
        title="도움말"
        description="근거를 추적할 수 있는 지원 문서를 만드는 흐름과 용어입니다."
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="flex flex-col gap-6">
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-section-title">흐름</h2>
            <ol className="mt-4 flex flex-col gap-4">
              {STEPS.map((s, i) => (
                <li key={s.title} className="flex gap-4">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-050 text-caption font-semibold text-primary-700 tabular-nums">
                    {i + 1}
                  </span>
                  <span className="flex min-w-0 flex-col gap-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="text-card-title">{s.title}</span>
                      <Link
                        href={s.href}
                        className="text-caption text-primary-600 underline-offset-2 hover:underline"
                      >
                        {s.label} →
                      </Link>
                    </span>
                    <span className="text-body text-text-600">{s.body}</span>
                  </span>
                </li>
              ))}
            </ol>
          </section>

          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-section-title">용어</h2>
            <dl className="mt-4 grid gap-x-4 gap-y-3 sm:grid-cols-[200px_1fr]">
              {TERMS.map((t) => (
                <div key={t.term} className="contents">
                  <dt className="text-body font-semibold">{t.term}</dt>
                  <dd className="text-body text-text-600">{t.definition}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>

        <aside className="flex h-fit flex-col gap-4">
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">AI가 하지 않는 것</h2>
            <ul className="mt-3 flex flex-col gap-2 text-caption text-text-600">
              {PRINCIPLES.map((p) => (
                <li key={p} className="flex gap-2">
                  <span
                    aria-hidden
                    className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary-600"
                  />
                  <span>{p}</span>
                </li>
              ))}
            </ul>
          </section>
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">상태 표시</h2>
            <ul className="mt-3 flex flex-col gap-2 text-caption">
              <li className="flex items-center gap-2">
                <Chip tone="verified">근거 있음</Chip>
                <span className="text-text-600">인용한 주장 모두에 Evidence가 있음</span>
              </li>
              <li className="flex items-center gap-2">
                <Chip tone="review">추론 · 주의</Chip>
                <span className="text-text-600">확인이 필요하거나 승인해야 하는 항목</span>
              </li>
              <li className="flex items-center gap-2">
                <Chip tone="expired">근거 없음</Chip>
                <span className="text-text-600">인용한 주장이 없는 문장</span>
              </li>
              <li className="flex items-center gap-2">
                <Chip tone="snapshot">스냅샷</Chip>
                <span className="text-text-600">생성 후 바뀌지 않는 기록</span>
              </li>
            </ul>
          </section>
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-card-title">아직 준비 중</h2>
            <ul className="mt-3 flex flex-col gap-1 text-caption text-text-600">
              {PENDING.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
            <p className="mt-3 text-caption text-text-600">
              로그인은 OIDC 제공자에 위임합니다(제공자 선택은 결정 대기).{" "}
              <Link href="/settings" className="text-primary-600 hover:underline">
                설정
              </Link>
              에서 문서 머리글 프로필을 입력할 수 있습니다.
            </p>
          </section>
        </aside>
      </div>
    </>
  );
}
