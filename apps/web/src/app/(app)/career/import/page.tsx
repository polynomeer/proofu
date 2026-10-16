import { CareerImport } from "@/components/career/CareerImport";
import { PageHeader } from "@/components/ui/PageHeader";

export const metadata = { title: "이력서 가져오기" };

export default function CareerImportPage() {
  return (
    <>
      <PageHeader
        title="이력서 가져오기"
        description="기존 이력서를 붙여넣으면 경력 기록 후보를 찾아 줍니다. 원문과 대조해 고른 것만 추가됩니다."
      />
      <CareerImport />
    </>
  );
}
