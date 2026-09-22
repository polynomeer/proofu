import type { Metadata } from "next";

import { ProjectForm } from "@/components/project/ProjectForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "새 프로젝트" };

export default async function NewProjectPage({
  searchParams,
}: {
  searchParams: Promise<{ careerEntryId?: string }>;
}) {
  const { careerEntryId } = await searchParams;
  const [{ data }, { data: settings }] = await Promise.all([
    api.GET("/career-entries", { params: { query: { limit: 100 } } }),
    api.GET("/me/settings").catch(() => ({ data: undefined })),
  ]);
  const options = (data?.items ?? []).map((e) => ({
    id: e.id,
    title: e.title,
    organization: e.organization,
  }));

  return (
    <>
      <PageHeader
        title="새 프로젝트"
        description="목표, 역할, 활동, 결과를 가진 작업 단위입니다. 성과는 저장 후 추가합니다."
      />
      <ProjectForm
        careerEntries={options}
        defaultCareerEntryId={careerEntryId}
        defaultVisibility={settings?.defaultVisibility ?? "PRIVATE"}
      />
    </>
  );
}
