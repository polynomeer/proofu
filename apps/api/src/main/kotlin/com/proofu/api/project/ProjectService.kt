package com.proofu.api.project

import com.proofu.api.audit.AuditLog
import com.proofu.api.career.CareerEntryRepository
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ProjectService(
    private val repository: ProjectRepository,
    private val careerEntries: CareerEntryRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        careerEntryId: UUID?,
        cursor: DateIdCursor?,
        limit: Int,
    ): ProjectPage {
        val rows = repository.page(workspace.workspaceId.value, careerEntryId, cursor?.date, cursor?.id, limit + 1)
        val page = rows.take(limit)
        val next =
            if (rows.size >
                limit
            ) {
                DateIdCursor(page.last().startDate ?: OPEN_ENDED, page.last().id).encode()
            } else {
                null
            }
        return ProjectPage(page.map(ProjectResponse::from), next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ProjectResponse = ProjectResponse.from(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: ProjectRequest,
    ): ProjectResponse {
        requireCareerEntryInWorkspace(workspace, request.careerEntryId)
        val project = request.toDomain(ProjectId(ids.next()), workspace.workspaceId, Revision.INITIAL)
        val saved = repository.saveAndFlush(ProjectEntity.from(project))
        val response = ProjectResponse.from(saved)
        audit.record(workspace, "project.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: ProjectRequest,
        expectedRevision: Long,
    ): ProjectResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        requireCareerEntryInWorkspace(workspace, request.careerEntryId)
        val before = ProjectResponse.from(entity)
        entity.apply(request.toDomain(ProjectId(entity.id), workspace.workspaceId, Revision(entity.revision).next()))
        val saved = repository.saveAndFlush(entity)
        val response = ProjectResponse.from(saved)
        audit.record(workspace, "project.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete; achievements of the project go to the trash with it. */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = ProjectResponse.from(entity)
        val now = Instant.now(clock)
        entity.deletedAt = now
        entity.revision += 1
        repository.saveAndFlush(entity)
        jdbc.update(
            "update achievements set deleted_at = ?, revision = revision + 1 where project_id = ? and deleted_at is null",
            java.sql.Timestamp.from(now),
            entity.id,
        )
        audit.record(workspace, "project.deleted", TARGET, entity.id, before = before)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ProjectEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    /** Attaching to an entry of another workspace is reported as "entry not found" (no existence leak). */
    private fun requireCareerEntryInWorkspace(
        workspace: WorkspaceContext,
        careerEntryId: UUID?,
    ) {
        if (careerEntryId == null) return
        careerEntries.findByIdAndWorkspaceIdAndDeletedAtIsNull(careerEntryId, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException("career_entry", careerEntryId)
    }

    private companion object {
        const val TARGET = "project"

        /** Sort placeholder for projects without a start date; must match the repository query. */
        val OPEN_ENDED: LocalDate = LocalDate.of(9999, 12, 31)
    }
}
