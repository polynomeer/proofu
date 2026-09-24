import type { Metadata } from "next";

import { SkillSection } from "@/components/skill/SkillSection";
import { ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { PAGE_LIMIT } from "@/lib/limits";

export const metadata: Metadata = { title: "기술" };
export const dynamic = "force-dynamic";

/** F01 기술 (docs/ux/information-architecture.md: 커리어 → 기술). */
export default async function SkillsPage() {
  const { data } = await api
    .GET("/skills", { params: { query: { limit: PAGE_LIMIT } } })
    .catch(() => ({ data: undefined }));

  return (
    <>
      <PageHeader
        title="기술"
        description="언어·프레임워크·도구를 워크스페이스에서 한 번만 등록하고 프로젝트에 연결합니다. 숙련도는 자기평가이며 매칭 점수에는 쓰이지 않습니다."
        action={
          <ButtonLink variant="secondary" href="/career">
            <Icon name="arrow-left" size={16} />
            커리어로
          </ButtonLink>
        }
      />
      {!data ? (
        <ErrorState
          title="기술 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : (
        <SkillSection initialItems={data.items} initialCursor={data.nextCursor ?? null} />
      )}
    </>
  );
}
