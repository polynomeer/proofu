import type { Metadata } from "next";
import Link from "next/link";

import { EvidenceTable } from "@/components/evidence/EvidenceTable";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Icon } from "@/components/ui/Icon";
import { PageHeader } from "@/components/ui/PageHeader";
import { filterChipClass } from "@/components/ui/filterChip";
import { api } from "@/lib/api";
import { EVIDENCE_TYPES, evidenceTypeLabel } from "@/lib/labels";

export const metadata: Metadata = { title: "Evidence" };

type SearchParams = Promise<{ type?: string; verification?: string; q?: string; cursor?: string }>;

const VERIFICATION_FILTERS = [
  { value: undefined, label: "전체" },
  { value: "USER_VERIFIED", label: "검증됨" },
  { value: "UNVERIFIED", label: "검토 필요" },
  { value: "EXPIRED", label: "만료" },
] as const;

function href(params: Record<string, string | undefined>) {
  const q = new URLSearchParams(
    Object.entries(params).filter((e): e is [string, string] => !!e[1]),
  );
  const s = q.toString();
  return s ? `/evidence?${s}` : "/evidence";
}

export default async function EvidencePage({ searchParams }: { searchParams: SearchParams }) {
  const { type, verification, q, cursor } = await searchParams;
  const typeFilter = EVIDENCE_TYPES.find((t) => t === type);
  const verificationFilter = VERIFICATION_FILTERS.find((v) => v.value === verification)?.value;

  const result = await api
    .GET("/evidence", {
      params: {
        query: { type: typeFilter, verification: verificationFilter, q, cursor, limit: 20 },
      },
    })
    .catch(() => ({ data: undefined }));

  const addAction = (
    <ButtonLink href="/evidence/new">
      <Icon name="plus" size={16} />
      Evidence 추가
    </ButtonLink>
  );
  const filtered = !!(typeFilter || verificationFilter || q);

  return (
    <>
      <PageHeader
        title="Evidence"
        description="주장을 뒷받침하는 근거 자료입니다. 링크, 저장소, 지표, 인증서, 메모를 등록하고 경력의 주장에 연결합니다."
        action={
          <div className="flex gap-2">
            <ButtonLink variant="secondary" href="/evidence/capabilities">
              역량 관리
            </ButtonLink>
            {addAction}
          </div>
        }
      />
      <div className="mb-4 flex flex-col gap-2">
        <nav aria-label="유형 필터" className="flex flex-wrap gap-2">
          <Link
            href={href({ verification: verificationFilter, q })}
            aria-current={!typeFilter ? "true" : undefined}
            className={filterChipClass(!typeFilter)}
          >
            전체
          </Link>
          {EVIDENCE_TYPES.map((t) => (
            <Link
              key={t}
              href={href({ type: t, verification: verificationFilter, q })}
              aria-current={typeFilter === t ? "true" : undefined}
              className={filterChipClass(typeFilter === t)}
            >
              {evidenceTypeLabel(t)}
            </Link>
          ))}
        </nav>
        <nav aria-label="검증 상태 필터" className="flex flex-wrap gap-2">
          {VERIFICATION_FILTERS.map((v) => (
            <Link
              key={v.label}
              href={href({ type: typeFilter, verification: v.value, q })}
              aria-current={verificationFilter === v.value ? "true" : undefined}
              className={filterChipClass(verificationFilter === v.value)}
            >
              {v.label}
            </Link>
          ))}
        </nav>
      </div>

      {!result.data ? (
        <ErrorState
          title="Evidence 목록을 불러오지 못했습니다"
          description="API 서버에 연결할 수 없거나 응답이 올바르지 않습니다. 잠시 후 새로고침하세요."
        />
      ) : result.data.items.length === 0 ? (
        <EmptyState
          title={filtered ? "조건에 맞는 근거가 없습니다" : "아직 등록된 Evidence가 없습니다"}
          description={
            filtered
              ? "다른 유형이나 상태를 선택해 보세요."
              : "성과 지표 캡처, 저장소 링크, 인증서 URL처럼 사실을 증명할 수 있는 자료부터 등록하세요."
          }
          action={filtered ? undefined : addAction}
        />
      ) : (
        <>
          <EvidenceTable items={result.data.items} />
          {result.data.nextCursor ? (
            <div className="mt-4 flex justify-center">
              <ButtonLink
                variant="secondary"
                href={href({
                  type: typeFilter,
                  verification: verificationFilter,
                  q,
                  cursor: result.data.nextCursor,
                })}
              >
                다음 페이지
              </ButtonLink>
            </div>
          ) : null}
        </>
      )}
    </>
  );
}
