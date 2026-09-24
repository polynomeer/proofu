package com.proofu.api.skill

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.project.ProjectRepository
import com.proofu.api.web.DuplicateResourceException
import com.proofu.api.web.InstantIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.career.Skill
import com.proofu.domain.career.SkillCategory
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import com.proofu.domain.common.SkillId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * F01 기술. A skill is one record per name across the workspace, so the service rejects a name
 * or alias that an existing skill already answers to before the unique index would (409).
 */
@Service
class SkillService(
    private val repository: SkillRepository,
    private val projects: ProjectRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        category: SkillCategory?,
        cursor: InstantIdCursor?,
        limit: Int,
    ): SkillPage {
        val rows =
            repository.page(workspace.workspaceId.value, category?.name, cursor?.at, cursor?.id, limit + 1)
        val page = rows.take(limit)
        val next =
            if (rows.size > limit) {
                InstantIdCursor(checkNotNull(page.last().createdAt), page.last().id).encode()
            } else {
                null
            }
        return SkillPage(page.map(SkillResponse::from), next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): SkillResponse = SkillResponse.from(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: SkillRequest,
    ): SkillResponse {
        val skill = request.toDomain(SkillId(ids.next()), workspace.workspaceId, Revision.INITIAL)
        requireNoCollision(workspace, skill, excludeId = null)
        val saved = repository.saveAndFlush(SkillEntity.from(skill))
        val response = SkillResponse.from(saved)
        audit.record(workspace, "skill.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: SkillRequest,
        expectedRevision: Long,
    ): SkillResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val before = SkillResponse.from(entity)
        val next =
            entity.toDomain().revisedTo(
                request.toDomain(SkillId(entity.id), workspace.workspaceId, Revision(entity.revision)),
            )
        requireNoCollision(workspace, next, excludeId = entity.id)
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        val response = SkillResponse.from(saved)
        audit.record(workspace, "skill.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete; the project links go at once so no project shows a skill that is in the trash. */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = SkillResponse.from(entity)
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        jdbc.update("delete from project_skills where skill_id = ?", entity.id)
        audit.record(workspace, "skill.deleted", TARGET, entity.id, before = before)
    }

    @Transactional(readOnly = true)
    fun ofProject(
        workspace: WorkspaceContext,
        projectId: UUID,
    ): SkillList {
        requireProject(workspace, projectId)
        return SkillList(linked(workspace, projectId))
    }

    /** Replaces the whole set; skills of another workspace are reported as not found. */
    @Transactional
    fun setProjectSkills(
        workspace: WorkspaceContext,
        projectId: UUID,
        request: ProjectSkillsRequest,
    ): SkillList {
        requireProject(workspace, projectId)
        val wanted = requireNotNull(request.skillIds).distinct()
        val found = repository.findByWorkspaceIdAndIdInAndDeletedAtIsNull(workspace.workspaceId.value, wanted)
        val missing = wanted - found.map { it.id }.toSet()
        if (missing.isNotEmpty()) throw ResourceNotFoundException(TARGET, missing.first())
        val before = linked(workspace, projectId)
        jdbc.update("delete from project_skills where project_id = ?", projectId)
        found.forEach {
            jdbc.update("insert into project_skills (project_id, skill_id) values (?, ?)", projectId, it.id)
        }
        val after = linked(workspace, projectId)
        audit.record(workspace, "project.skills_set", "project", projectId, before = before, after = after)
        return SkillList(after)
    }

    private fun linked(
        workspace: WorkspaceContext,
        projectId: UUID,
    ): List<SkillResponse> =
        jdbc
            .query(
                """
                select s.id from project_skills ps join skills s on s.id = ps.skill_id
                where ps.project_id = ? and s.workspace_id = ? and s.deleted_at is null
                order by s.canonical_name
                """.trimIndent(),
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                projectId,
                workspace.workspaceId.value,
            ).let { orderedIds ->
                val byId =
                    repository
                        .findByWorkspaceIdAndIdInAndDeletedAtIsNull(workspace.workspaceId.value, orderedIds)
                        .associateBy { it.id }
                orderedIds.mapNotNull { byId[it] }.map(SkillResponse::from)
            }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): SkillEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun requireProject(
        workspace: WorkspaceContext,
        projectId: UUID,
    ) {
        projects.findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException("project", projectId)
    }

    private fun requireNoCollision(
        workspace: WorkspaceContext,
        skill: Skill,
        excludeId: UUID?,
    ) {
        val clash =
            repository
                .collisions(workspace.workspaceId.value, skill.allNames.toTypedArray(), excludeId)
                .firstOrNull() ?: return
        throw DuplicateResourceException(TARGET, clash.canonicalName)
    }

    private companion object {
        const val TARGET = "skill"
    }
}
