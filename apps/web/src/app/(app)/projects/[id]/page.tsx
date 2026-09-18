import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { AchievementSection } from "@/components/achievement/AchievementSection";
import { ClaimPanel } from "@/components/claim/ClaimPanel";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { DeleteResourceButton } from "@/components/ui/DeleteResourceButton";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { formatDateTime, formatMonth } from "@/lib/format";
import { visibilityLabel } from "@/lib/labels";

type Params = Promise<{ id: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const { data } = await api
    .GET("/projects/{id}", { params: { path: { id } } })
    .catch(() => ({ data: undefined }));
  return { title: data?.name ?? "프로젝트" };
}

export default async function ProjectPage({ params }: { params: Params }) {
  const { id } = await params;
  const { data: project } = await api.GET("/projects/{id}", { params: { path: { id } } });
  if (!project) notFound();

  const [achievements, claims, entry] = await Promise.all([
    api.GET("/projects/{id}/achievements", { params: { path: { id } } }),
    api.GET("/claims", { params: { query: { projectId: id } } }),
    project.careerEntryId
      ? api.GET("/career-entries/{id}", { params: { path: { id: project.careerEntryId } } })
      : Promise.resolve({ data: undefined }),
  ]);

  return (
    <>
      <PageHeader
        title={project.name}
        description={project.role}
        action={
          <div className="flex gap-2">
            <ButtonLink variant="secondary" href={`/projects/${project.id}/edit`}>
              편집
            </ButtonLink>
            <DeleteResourceButton
              resource="project"
              id={project.id}
              title={project.name}
              redirectTo={project.careerEntryId ? `/career/${project.careerEntryId}` : "/career"}
              note="연결된 성과도 함께 휴지통으로 이동합니다."
            />
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-6">
          <section className="rounded-md border border-border-300 bg-surface-000 p-6">
            <h2 className="text-section-title">요약</h2>
            <p className="mt-3 text-body whitespace-pre-line">{project.summary}</p>
          </section>
          <AchievementSection
            projectId={project.id}
            initialItems={achievements.data?.items ?? []}
            initialClaims={claims.data?.items ?? []}
          />
          <section className="rounded-md border border-border-300 bg-surface-000 p-4">
            <ClaimPanel
              heading="프로젝트 전체에 대한 주장"
              source={{ type: "PROJECT", id: project.id }}
              initialClaims={(claims.data?.items ?? []).filter((c) =>
                c.sources.some((s) => s.type === "PROJECT" && s.id === project.id),
              )}
              defaultText={`${project.name}에서 ${project.role}로 ${project.summary}`}
            />
          </section>
        </div>

        <aside className="flex h-fit flex-col gap-4 rounded-md border border-border-300 bg-surface-000 p-6">
          <h2 className="text-card-title">정보</h2>
          <dl className="grid grid-cols-[96px_1fr] gap-x-3 gap-y-2 text-body">
            <dt className="text-text-600">연결 경력</dt>
            <dd>
              {entry.data ? (
                <Link
                  href={`/career/${entry.data.id}`}
                  className="text-primary-600 underline-offset-2 hover:underline"
                >
                  {entry.data.title}
                </Link>
              ) : (
                <span className="text-text-600">독립 프로젝트</span>
              )}
            </dd>
            <dt className="text-text-600">기간</dt>
            <dd className="tabular-nums">
              {project.startDate || project.endDate
                ? `${project.startDate ? formatMonth(project.startDate) : "?"} – ${project.endDate ? formatMonth(project.endDate) : "현재"}`
                : "미정"}
            </dd>
            <dt className="text-text-600">팀 규모</dt>
            <dd>{project.teamSize ? `${project.teamSize}명` : "—"}</dd>
            <dt className="text-text-600">공개 범위</dt>
            <dd>
              <Chip tone={project.visibility === "PRIVATE" ? "private" : "neutral"}>
                {visibilityLabel(project.visibility)}
              </Chip>
            </dd>
            <dt className="text-text-600">revision</dt>
            <dd className="tabular-nums">{project.revision}</dd>
            <dt className="text-text-600">수정</dt>
            <dd className="text-caption text-text-600">{formatDateTime(project.updatedAt)}</dd>
          </dl>
          {project.links.length > 0 ? (
            <>
              <h3 className="text-card-title">링크</h3>
              <ul className="flex flex-col gap-1 text-body">
                {project.links.map((l) => (
                  <li key={l.url}>
                    <a
                      href={l.url}
                      target="_blank"
                      rel="noreferrer noopener"
                      className="break-all text-primary-600 underline-offset-2 hover:underline"
                    >
                      {l.label}
                    </a>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
        </aside>
      </div>
    </>
  );
}
