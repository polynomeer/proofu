package com.proofu.api.project

import com.proofu.domain.career.Project
import com.proofu.domain.career.ProjectLink
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ProjectLinkRequest(
    @field:NotBlank @field:Size(max = 80, message = "{max}자 이하로 입력하세요") val label: String?,
    @field:NotBlank @field:Size(max = 2048, message = "{max}자 이하로 입력하세요") val url: String?,
)

/** Matches `ProjectInput` in the contract. */
data class ProjectRequest(
    val careerEntryId: UUID? = null,
    @field:NotBlank @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val name: String?,
    @field:NotBlank @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val role: String?,
    @field:NotBlank val summary: String?,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:Min(1) val teamSize: Int? = null,
    @field:Valid @field:Size(max = Project.MAX_LINKS) val links: List<ProjectLinkRequest> = emptyList(),
    val visibility: Visibility? = null,
    val revision: Long? = null,
) {
    fun toDomain(
        id: ProjectId,
        workspaceId: WorkspaceId,
        revision: Revision,
        defaultVisibility: Visibility = Visibility.PRIVATE,
    ): Project =
        Project(
            id = id,
            workspaceId = workspaceId,
            name = requireNotNull(name).trim(),
            role = requireNotNull(role).trim(),
            summary = requireNotNull(summary).trim(),
            careerEntryId = careerEntryId?.let(::CareerEntryId),
            startDate = startDate,
            endDate = endDate,
            teamSize = teamSize,
            links = links.map { ProjectLink(requireNotNull(it.label).trim(), requireNotNull(it.url).trim()) },
            visibility = visibility ?: defaultVisibility,
            revision = revision,
        )
}

data class ProjectResponse(
    val id: UUID,
    val careerEntryId: UUID?,
    val name: String,
    val role: String,
    val summary: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val teamSize: Int?,
    val links: List<ProjectLinkJson>,
    val visibility: Visibility,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: ProjectEntity) =
            ProjectResponse(
                id = e.id,
                careerEntryId = e.careerEntryId,
                name = e.name,
                role = e.role,
                summary = e.summary,
                startDate = e.startDate,
                endDate = e.endDate,
                teamSize = e.teamSize,
                links = e.links,
                visibility = e.visibility,
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class ProjectPage(
    val items: List<ProjectResponse>,
    val nextCursor: String?,
)
