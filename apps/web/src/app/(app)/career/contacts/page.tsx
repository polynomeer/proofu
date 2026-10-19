import type { Metadata } from "next";

import { ContactSection } from "@/components/contact/ContactSection";
import { RelationFilter } from "@/components/contact/RelationFilter";
import { ButtonLink } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { CONTACT_RELATIONS } from "@/lib/labels";
import { PAGE_LIMIT, PICKER_LIMIT } from "@/lib/limits";

export const metadata: Metadata = { title: "인맥" };
export const dynamic = "force-dynamic";

type SearchParams = Promise<{ relation?: string; cursor?: string }>;

/** F14 인맥 (docs/ux/information-architecture.md: 커리어 → 인맥). */
export default async function ContactsPage({ searchParams }: { searchParams: SearchParams }) {
  const { relation: relationParam, cursor } = await searchParams;
  const relation = CONTACT_RELATIONS.find((r) => r === relationParam);

  const [contacts, entries, projects] = await Promise.all([
    api
      .GET("/contacts", { params: { query: { relation, cursor, limit: PAGE_LIMIT } } })
      .catch(() => ({ data: undefined })),
    api
      .GET("/career-entries", { params: { query: { limit: PICKER_LIMIT } } })
      .catch(() => ({ data: undefined })),
    api
      .GET("/projects", { params: { query: { limit: PICKER_LIMIT } } })
      .catch(() => ({ data: undefined })),
  ]);

  return (
    <>
      <PageHeader
        back={{ href: "/career", label: "커리어" }}
        title="인맥"
        description="함께 일한 사람을 적어 둡니다. 상대에게는 아무것도 알리지 않고, 기록은 모델에 보내지 않습니다. 나중에 어떤 주장을 누구에게 확인받을지 고를 때 씁니다."
        action={
          <ButtonLink variant="secondary" href="/career">
            <Icon name="arrow-left" size={16} />
            커리어로
          </ButtonLink>
        }
      />
      <div className="mb-4">
        <RelationFilter current={relation} />
      </div>
      {!contacts.data ? (
        <ErrorState
          title="인맥을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : (
        <ContactSection
          initialItems={contacts.data.items}
          initialCursor={contacts.data.nextCursor ?? null}
          relation={relation}
          entries={entries.data?.items ?? []}
          projects={projects.data?.items ?? []}
          pickersTruncated={Boolean(entries.data?.nextCursor) || Boolean(projects.data?.nextCursor)}
        />
      )}
    </>
  );
}
