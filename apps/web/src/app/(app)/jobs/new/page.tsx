import type { Metadata } from "next";

import { JobPostingForm } from "@/components/jobs/JobPostingForm";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "공고 저장" };

/** `?postingId=` prefills company, role and url so a re-paste lands on the same posting. */
export default async function NewJobPostingPage({
  searchParams,
}: {
  searchParams: Promise<{ postingId?: string }>;
}) {
  const { postingId } = await searchParams;
  const { data: posting } = postingId
    ? await api
        .GET("/job-postings/{id}", { params: { path: { id: postingId } } })
        .catch(() => ({ data: undefined }))
    : { data: undefined };

  return (
    <>
      <PageHeader
        title={posting ? "새 버전 붙여넣기" : "공고 저장"}
        description={
          posting
            ? `${posting.company} · ${posting.roleTitle}의 최신 본문을 붙여넣으면 새 스냅샷이 쌓입니다.`
            : "채용 페이지 본문을 붙여넣으세요. 저장된 원문은 변경할 수 없고, 내용이 바뀌면 새 버전으로 기록됩니다."
        }
      />
      <JobPostingForm
        initial={
          posting
            ? {
                company: posting.company,
                roleTitle: posting.roleTitle,
                sourceUrl: posting.canonicalUrl ?? "",
              }
            : undefined
        }
      />
    </>
  );
}
