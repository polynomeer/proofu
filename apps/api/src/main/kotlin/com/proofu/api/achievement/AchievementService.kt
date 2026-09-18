package com.proofu.api.achievement

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.project.ProjectRepository
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class AchievementService(
    private val repository: AchievementRepository,
    private val projects: ProjectRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun listForProject(
        workspace: WorkspaceContext,
        projectId: UUID,
    ): AchievementList {
        requireProject(workspace, projectId)
        val rows =
            repository.findAllByProjectIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                projectId,
                workspace.workspaceId.value,
            )
        return AchievementList(rows.map(AchievementResponse::from))
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): AchievementResponse = AchievementResponse.from(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        projectId: UUID,
        request: AchievementRequest,
    ): AchievementResponse {
        requireProject(workspace, projectId)
        val achievement =
            request.toDomain(AchievementId(ids.next()), workspace.workspaceId, ProjectId(projectId), Revision.INITIAL)
        val saved = repository.saveAndFlush(AchievementEntity.from(achievement))
        val response = AchievementResponse.from(saved)
        audit.record(workspace, "achievement.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: AchievementRequest,
        expectedRevision: Long,
    ): AchievementResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val before = AchievementResponse.from(entity)
        entity.apply(
            request.toDomain(
                AchievementId(entity.id),
                workspace.workspaceId,
                ProjectId(entity.projectId),
                Revision(entity.revision).next(),
            ),
        )
        val saved = repository.saveAndFlush(entity)
        val response = AchievementResponse.from(saved)
        audit.record(workspace, "achievement.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = AchievementResponse.from(entity)
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "achievement.deleted", TARGET, entity.id, before = before)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): AchievementEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun requireProject(
        workspace: WorkspaceContext,
        projectId: UUID,
    ) {
        projects.findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException("project", projectId)
    }

    private companion object {
        const val TARGET = "achievement"
    }
}
