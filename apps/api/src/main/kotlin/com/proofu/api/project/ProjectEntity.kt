package com.proofu.api.project

import com.proofu.domain.career.Project
import com.proofu.domain.career.ProjectLink
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "projects")
class ProjectEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "career_entry_id")
    var careerEntryId: UUID?,
    var name: String,
    var role: String,
    @Column(columnDefinition = "text")
    var summary: String,
    @Column(name = "start_date")
    var startDate: LocalDate?,
    @Column(name = "end_date")
    var endDate: LocalDate?,
    @Column(name = "team_size")
    var teamSize: Int?,
    @Convert(converter = ProjectLinksConverter::class)
    @JdbcTypeCode(SqlTypes.JSON)
    var links: List<ProjectLinkJson>,
    @Enumerated(EnumType.STRING)
    var visibility: Visibility,
    var revision: Long,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun apply(project: Project) {
        require(project.id.value == id && project.workspaceId.value == workspaceId) { "entity identity mismatch" }
        careerEntryId = project.careerEntryId?.value
        name = project.name
        role = project.role
        summary = project.summary
        startDate = project.startDate
        endDate = project.endDate
        teamSize = project.teamSize
        links = project.links.map { ProjectLinkJson(it.label, it.url) }
        visibility = project.visibility
        revision = project.revision.value
    }

    fun toDomain(): Project =
        Project(
            id = ProjectId(id),
            workspaceId = WorkspaceId(workspaceId),
            name = name,
            role = role,
            summary = summary,
            careerEntryId = careerEntryId?.let(::CareerEntryId),
            startDate = startDate,
            endDate = endDate,
            teamSize = teamSize,
            links = links.map { ProjectLink(it.label, it.url) },
            visibility = visibility,
            revision = Revision(revision),
        )

    companion object {
        fun from(project: Project): ProjectEntity =
            ProjectEntity(
                id = project.id.value,
                workspaceId = project.workspaceId.value,
                careerEntryId = project.careerEntryId?.value,
                name = project.name,
                role = project.role,
                summary = project.summary,
                startDate = project.startDate,
                endDate = project.endDate,
                teamSize = project.teamSize,
                links = project.links.map { ProjectLinkJson(it.label, it.url) },
                visibility = project.visibility,
                revision = project.revision.value,
            )
    }
}
