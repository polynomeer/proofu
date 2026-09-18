import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";

export default function ProjectNotFound() {
  return (
    <EmptyState
      title="프로젝트를 찾을 수 없습니다"
      description="삭제되었거나 이 워크스페이스에 없는 항목입니다."
      action={
        <ButtonLink variant="secondary" href="/career">
          커리어 목록으로
        </ButtonLink>
      }
    />
  );
}
