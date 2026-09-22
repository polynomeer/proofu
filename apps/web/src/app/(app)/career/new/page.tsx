import type { Metadata } from "next";

import { CareerEntryForm } from "@/components/career/CareerEntryForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "새 경력 추가" };
export const dynamic = "force-dynamic";

export default async function NewCareerEntryPage() {
  const { data: settings } = await api.GET("/me/settings").catch(() => ({ data: undefined }));
  return (
    <>
      <PageHeader
        title="새 경력 추가"
        description="사실만 입력하세요. 성과 수치와 근거(Evidence)는 저장 후 항목에 연결합니다."
      />
      <CareerEntryForm defaultVisibility={settings?.defaultVisibility ?? "PRIVATE"} />
    </>
  );
}
