import type { Metadata } from "next";

import { EvidenceForm } from "@/components/evidence/EvidenceForm";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata: Metadata = { title: "Evidence 추가" };

export default function NewEvidencePage() {
  return (
    <>
      <PageHeader
        title="Evidence 추가"
        description="원본 또는 원본의 위치를 보존합니다. 등록 후 경력의 주장에 연결하세요."
      />
      <EvidenceForm />
    </>
  );
}
