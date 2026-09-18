import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";

export default function ApplicationNotFound() {
  return (
    <EmptyState
      title="지원 건을 찾을 수 없습니다"
      description="삭제되었거나 이 워크스페이스에 없는 항목입니다."
      action={
        <ButtonLink variant="secondary" href="/applications">
          지원 관리로
        </ButtonLink>
      }
    />
  );
}
