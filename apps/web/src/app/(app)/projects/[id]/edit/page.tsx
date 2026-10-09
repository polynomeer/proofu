import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ProjectForm } from "@/components/project/ProjectForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "프로젝트 편집" };

export default async function EditProjectPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [{ data: project }, { data: entries }] = await Promise.all([
    api.GET("/projects/{id}", { params: { path: { id } } }),
    api.GET("/career-entries", { params: { query: { limit: 100 } } }),
  ]);
  if (!project) notFound();
  const options = (entries?.items ?? []).map((e) => ({
    id: e.id,
    title: e.title,
    organization: e.organization,
  }));

  return (
    <>
      <PageHeader
        back={{ href: `/projects/${project.id}`, label: project.name }}
        title="프로젝트 편집"
        description={project.name}
      />
      <ProjectForm project={project} careerEntries={options} />
    </>
  );
}
