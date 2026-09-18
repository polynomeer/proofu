import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";

export default function JobPostingNotFound() {
  return (
    <EmptyState
      title="공고를 찾을 수 없습니다"
      description="삭제되었거나 이 워크스페이스에 없는 항목입니다."
      action={
        <ButtonLink variant="secondary" href="/jobs">
          채용공고 목록으로
        </ButtonLink>
      }
    />
  );
}
