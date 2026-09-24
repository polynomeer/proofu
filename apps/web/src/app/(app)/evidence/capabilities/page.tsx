import type { Metadata } from "next";

import { CapabilitySection } from "@/components/capability/CapabilitySection";
import { ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { PAGE_LIMIT, PICKER_LIMIT } from "@/lib/limits";

export const metadata: Metadata = { title: "역량" };
export const dynamic = "force-dynamic";

/** E03 역량 (docs/ux/information-architecture.md: Evidence → 역량). */
export default async function CapabilitiesPage() {
  const [capabilities, evidence] = await Promise.all([
    api
      .GET("/capabilities", { params: { query: { limit: PAGE_LIMIT } } })
      .catch(() => ({ data: undefined })),
    api
      .GET("/evidence", { params: { query: { limit: PICKER_LIMIT } } })
      .catch(() => ({ data: undefined })),
  ]);

  return (
    <>
      <PageHeader
        title="역량"
        description="'무엇을 할 수 있는가'를 정의하고 Evidence를 연결합니다. 수준은 자기평가이며, 연결된 Evidence는 근거 상태로만 표시합니다."
        action={
          <ButtonLink variant="secondary" href="/evidence">
            <Icon name="arrow-left" size={16} />
            Evidence로
          </ButtonLink>
        }
      />
      {!capabilities.data ? (
        <ErrorState
          title="역량 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : (
        <CapabilitySection
          initialItems={capabilities.data.items}
          initialCursor={capabilities.data.nextCursor ?? null}
          evidence={evidence.data?.items ?? []}
          evidenceCapped={(evidence.data?.items ?? []).length >= PICKER_LIMIT}
        />
      )}
    </>
  );
}
