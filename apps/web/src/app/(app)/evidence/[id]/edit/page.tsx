import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { EvidenceForm } from "@/components/evidence/EvidenceForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "Evidence 편집" };

export default async function EditEvidencePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { data: evidence } = await api.GET("/evidence/{id}", { params: { path: { id } } });
  if (!evidence) notFound();
  return (
    <>
      <PageHeader
        back={{ href: `/evidence/${evidence.id}`, label: evidence.title }}
        title="Evidence 편집"
        description={evidence.title}
      />
      <EvidenceForm evidence={evidence} />
    </>
  );
}
