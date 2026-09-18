import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { CareerEntryForm } from "@/components/career/CareerEntryForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "경력 편집" };

export default async function EditCareerEntryPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { data: entry } = await api.GET("/career-entries/{id}", { params: { path: { id } } });
  if (!entry) notFound();

  return (
    <>
      <PageHeader title="경력 편집" description={entry.title} />
      <CareerEntryForm entry={entry} />
    </>
  );
}
