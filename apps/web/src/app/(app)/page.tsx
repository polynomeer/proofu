import type { Metadata } from "next";

import { ButtonLink } from "@/components/ui/Button";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = { title: "커리어 대시보드" };

/** D01 placeholder: KPI cards wire up once the dashboard endpoint exists. */
export default function DashboardPage() {
  return (
    <>
      <PageHeader
        title="커리어 대시보드"
        description="당신의 경험이 증명되는 커리어, ProofU와 함께하세요."
        action={
          <ButtonLink href="/career/new">
            <Icon name="plus" size={16} />새 경력 추가
          </ButtonLink>
        }
      />
      <section className="rounded-md border border-border-300 bg-surface-000 p-6">
        <h2 className="text-section-title">아직 데이터가 없습니다</h2>
        <p className="mt-2 max-w-prose text-body text-text-600">
          첫 경력을 등록하면 타임라인, 주요 스킬, 최근 Evidence가 여기에 표시됩니다. 샘플 데이터는
          자동으로 만들지 않습니다.
        </p>
      </section>
    </>
  );
}
