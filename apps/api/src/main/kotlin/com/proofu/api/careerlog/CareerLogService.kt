package com.proofu.api.careerlog

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.career.CareerLogLink
import com.proofu.domain.career.CareerLogLinkType
import com.proofu.domain.career.CareerLogPromotion
import com.proofu.domain.career.CareerLogPromotionType
import com.proofu.domain.common.CareerLogId
import com.proofu.domain.common.IdGenerator
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * F20 커리어 로그. A log is written while the work happens and later promoted into an achievement
 * or evidence. Promotion is a copy, so this service only records that it happened — the
 * achievement itself is created through its own endpoint, and afterwards the two are
 * indistinguishable (docs/domain/career-data-model.md §커리어 로그).
 */
@Service
class CareerLogService(
    private val repository: CareerLogRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        promoted: Boolean?,
        cursor: DateIdCursor?,
        limit: Int,
    ): CareerLogPage {
        val rows =
            repository.page(
                workspaceId = workspace.workspaceId.value,
                promoted = promoted,
                cursorDate = cursor?.date,
                cursorId = cursor?.id,
                limit = limit + 1,
            )
        val page = rows.take(limit)
        val links = linksOf(page.map { it.id })
        val next = if (rows.size > limit) DateIdCursor(page.last().occurredAt, page.last().id).encode() else null
        return CareerLogPage(page.map { CareerLogResponse.from(it, links[it.id].orEmpty()) }, next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): CareerLogResponse {
        val entity = find(workspace, id)
        return CareerLogResponse.from(entity, linksOf(listOf(entity.id))[entity.id].orEmpty())
    }

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: CareerLogRequest,
    ): CareerLogResponse {
        val log = request.toNewDomain(CareerLogId(ids.next()), workspace.workspaceId, today())
        requireLinkTargets(workspace, log.links)
        val saved = repository.saveAndFlush(CareerLogEntity.from(log))
        replaceLinks(saved.id, log.links)
        val response = CareerLogResponse.from(saved, log.links)
        audit.record(workspace, "career_log.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: CareerLogRequest,
        expectedRevision: Long,
    ): CareerLogResponse {
        val entity = lock(workspace, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val currentLinks = linksOf(listOf(entity.id))[entity.id].orEmpty()
        val before = CareerLogResponse.from(entity, currentLinks)
        val next = entity.toDomain(currentLinks).let { it.revisedTo(request.toRevisedDomain(it, today())) }
        requireLinkTargets(workspace, next.links)
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        replaceLinks(saved.id, next.links)
        val response = CareerLogResponse.from(saved, next.links)
        audit.record(workspace, "career_log.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /**
     * The log produced an achievement (or evidence) that the caller already created. A second
     * promotion is refused by the domain, since it would be a second copy of the same line.
     */
    @Transactional
    fun promote(
        workspace: WorkspaceContext,
        id: UUID,
        request: CareerLogPromotionRequest,
    ): CareerLogResponse {
        val entity = lock(workspace, id)
        val links = linksOf(listOf(entity.id))[entity.id].orEmpty()
        val before = CareerLogResponse.from(entity, links)
        val targetType = requireNotNull(request.targetType)
        val targetId = requireNotNull(request.targetId)
        val (table, target) =
            when (targetType) {
                CareerLogPromotionType.ACHIEVEMENT -> "achievements" to "achievement"
                CareerLogPromotionType.EVIDENCE -> "evidence" to "evidence"
            }
        requireExists(table, target, listOf(targetId), workspace)
        val promoted =
            entity.toDomain(links).promotedTo(CareerLogPromotion.Target(targetType, targetId), Instant.now(clock))
        entity.apply(promoted)
        val saved = repository.saveAndFlush(entity)
        val response = CareerLogResponse.from(saved, links)
        audit.record(workspace, "career_log.promoted", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete: the row stays for the trash window (docs/data/retention.md §커리어 로그). */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity = lock(workspace, id)
        val before = CareerLogResponse.from(entity, linksOf(listOf(entity.id))[entity.id].orEmpty())
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "career_log.deleted", TARGET, entity.id, before = before)
    }

    private fun today(): LocalDate = LocalDate.ofInstant(Instant.now(clock), clock.zone)

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): CareerLogEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun lock(
        workspace: WorkspaceContext,
        id: UUID,
    ): CareerLogEntity =
        repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    /** Links of several logs in one query, so a page does not ask per row. */
    private fun linksOf(logIds: List<UUID>): Map<UUID, List<CareerLogLink>> {
        if (logIds.isEmpty()) return emptyMap()
        val placeholders = logIds.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select log_id, career_entry_id, project_id from career_log_links
                where log_id in ($placeholders)
                order by career_entry_id nulls last, project_id nulls last
                """.trimIndent(),
                { rs, _ ->
                    val logId = rs.getObject("log_id", UUID::class.java)
                    val entryId = rs.getObject("career_entry_id", UUID::class.java)
                    val projectId = rs.getObject("project_id", UUID::class.java)
                    logId to
                        if (entryId != null) {
                            CareerLogLink(CareerLogLinkType.CAREER_ENTRY, entryId)
                        } else {
                            CareerLogLink(CareerLogLinkType.PROJECT, checkNotNull(projectId))
                        }
                },
                *logIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }

    /** The set is replaced whole, like every other career link. */
    private fun replaceLinks(
        logId: UUID,
        links: List<CareerLogLink>,
    ) {
        jdbc.update("delete from career_log_links where log_id = ?", logId)
        links.forEach {
            when (it.type) {
                CareerLogLinkType.CAREER_ENTRY ->
                    jdbc.update(
                        "insert into career_log_links (log_id, career_entry_id) values (?, ?)",
                        logId,
                        it.id,
                    )

                CareerLogLinkType.PROJECT ->
                    jdbc.update("insert into career_log_links (log_id, project_id) values (?, ?)", logId, it.id)
            }
        }
    }

    /** A link or a promotion target of another workspace is reported as not found, never 403. */
    private fun requireLinkTargets(
        workspace: WorkspaceContext,
        links: List<CareerLogLink>,
    ) {
        requireExists(
            "career_entries",
            "career_entry",
            links.filter { it.type == CareerLogLinkType.CAREER_ENTRY }.map { it.id },
            workspace,
        )
        requireExists(
            "projects",
            "project",
            links.filter { it.type == CareerLogLinkType.PROJECT }.map { it.id },
            workspace,
        )
    }

    private fun requireExists(
        table: String,
        target: String,
        wanted: List<UUID>,
        workspace: WorkspaceContext,
    ) {
        if (wanted.isEmpty()) return
        val placeholders = wanted.joinToString(",") { "?" }
        val found =
            jdbc
                .query(
                    """
                    select id from $table
                    where workspace_id = ? and deleted_at is null and id in ($placeholders)
                    """.trimIndent(),
                    { rs, _ -> rs.getObject("id", UUID::class.java) },
                    workspace.workspaceId.value,
                    *wanted.toTypedArray(),
                ).toSet()
        val missing = wanted.firstOrNull { it !in found } ?: return
        throw ResourceNotFoundException(target, missing)
    }

    private companion object {
        const val TARGET = "career_log"
    }
}
