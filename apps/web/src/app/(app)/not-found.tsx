import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";

/** Any unknown path inside the app shell; per-resource pages keep their own wording. */
export default function AppNotFound() {
  return (
    <>
      <PageHeader title="페이지를 찾을 수 없습니다" />
      <EmptyState
        title="주소를 다시 확인해 주세요"
        description="이동했거나 삭제된 화면일 수 있습니다. 대시보드에서 다시 시작하세요."
        action={<ButtonLink href="/">대시보드로</ButtonLink>}
      />
    </>
  );
}
