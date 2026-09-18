package com.proofu.api.application

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.jobs.JobPostingSnapshotRepository
import com.proofu.api.jobs.SnapshotSummary
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.applications.Application
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.events.ApplicationStatusChanged
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ApplicationService(
    private val repository: ApplicationRepository,
    private val snapshots: JobPostingSnapshotRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        status: ApplicationStatus?,
        includeTerminal: Boolean,
    ): ApplicationList {
        val rows = repository.board(workspace.workspaceId.value, status?.name, includeTerminal, TERMINAL, BOARD_LIMIT)
        val postingIds = postingIds(rows.map { it.snapshotId })
        val changedAt = lastStatusChange(rows.map { it.id })
        return ApplicationList(
            rows.map {
                ApplicationResponse.from(
                    it,
                    postingIds.getValue(it.snapshotId),
                    changedAt[it.id] ?: checkNotNull(it.createdAt),
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ApplicationDetail = detail(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: CreateApplicationRequest,
    ): ApplicationResponse {
        val snapshotId = requireNotNull(request.snapshotId)
        val snapshot =
            snapshots.findByIdInWorkspace(snapshotId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("job_posting_snapshot", snapshotId)
        val posting =
            jdbc.queryForMap("select company, role_title from job_postings where id = ?", snapshot.postingId)
        val application =
            Application(
                id = ApplicationId(ids.next()),
                workspaceId = workspace.workspaceId,
                snapshotId = JobPostingSnapshotId(snapshot.id),
                company = posting["company"] as String,
                roleTitle = posting["role_title"] as String,
            )
        val saved = repository.saveAndFlush(ApplicationEntity.from(application, request.deadlineAt))
        val now = Instant.now(clock)
        recordEvent(saved.id, null, saved.status, null, now)
        val response = ApplicationResponse.from(saved, snapshot.postingId, now)
        audit.record(workspace, "application.created", TARGET, saved.id, after = response)
        events.publishEvent(ApplicationStatusChanged(ids.next(), now, ApplicationId(saved.id), null, saved.status))
        return response
    }

    @Transactional
    fun transition(
        workspace: WorkspaceContext,
        id: UUID,
        request: TransitionRequest,
    ): ApplicationDetail {
        val entity = lock(workspace, id)
        if (entity.version !=
            requireNotNull(request.version)
        ) {
            throw StaleVersionException(TARGET, request.version, entity.version)
        }
        val to = requireNotNull(request.to)
        if (to == ApplicationStatus.HANDED_OFF_TO_ITERVIEW) {
            throw DomainRuleViolation(
                "handoff to iterview happens through the interview handoff, not a plain transition",
            )
        }
        val before = summary(entity)
        val now = Instant.now(clock)
        val (next, event) = entity.toDomain().transition(to, now)
        entity.apply(next)
        repository.saveAndFlush(entity)
        recordEvent(entity.id, event.from, event.to, request.note?.trim()?.ifEmpty { null }, now)
        val response = detail(entity)
        audit.record(
            workspace,
            "application.status_changed",
            TARGET,
            entity.id,
            before = before,
            after = summary(entity),
        )
        events.publishEvent(ApplicationStatusChanged(ids.next(), now, ApplicationId(entity.id), event.from, event.to))
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: UpdateApplicationRequest,
    ): ApplicationResponse {
        val entity = lock(workspace, id)
        if (entity.version !=
            requireNotNull(request.version)
        ) {
            throw StaleVersionException(TARGET, request.version, entity.version)
        }
        val before = summary(entity)
        entity.deadlineAt = request.deadlineAt
        entity.version += 1
        repository.saveAndFlush(entity)
        val response = summary(entity)
        audit.record(workspace, "application.updated", TARGET, entity.id, before = before, after = response)
        return response
    }

    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity = lock(workspace, id)
        val before = summary(entity)
        entity.deletedAt = Instant.now(clock)
        entity.version += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "application.deleted", TARGET, entity.id, before = before)
    }

    private fun detail(entity: ApplicationEntity): ApplicationDetail {
        val snapshot = snapshots.findById(entity.snapshotId).orElseThrow()
        val history =
            jdbc.query(
                "select from_status, to_status, note, occurred_at from application_status_events where application_id = ? order by occurred_at, id",
                { rs, _ ->
                    ApplicationStatusEventResponse(
                        from = rs.getString("from_status")?.let(ApplicationStatus::valueOf),
                        to = ApplicationStatus.valueOf(rs.getString("to_status")),
                        note = rs.getString("note"),
                        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                    )
                },
                entity.id,
            )
        val changedAt = history.lastOrNull()?.occurredAt ?: checkNotNull(entity.createdAt)
        return ApplicationDetail.from(
            ApplicationResponse.from(entity, snapshot.postingId, changedAt),
            history,
            SnapshotSummary.from(snapshot),
        )
    }

    private fun summary(entity: ApplicationEntity): ApplicationResponse {
        val postingId = postingIds(listOf(entity.snapshotId)).getValue(entity.snapshotId)
        val changedAt = lastStatusChange(listOf(entity.id))[entity.id] ?: checkNotNull(entity.createdAt)
        return ApplicationResponse.from(entity, postingId, changedAt)
    }

    /** Append-only history (the table's trigger rejects updates and deletes). */
    private fun recordEvent(
        applicationId: UUID,
        from: ApplicationStatus?,
        to: ApplicationStatus,
        note: String?,
        at: Instant,
    ) {
        jdbc.update(
            "insert into application_status_events (id, application_id, from_status, to_status, note, occurred_at) values (?, ?, ?, ?, ?, ?)",
            ids.next(),
            applicationId,
            from?.name,
            to.name,
            note,
            Timestamp.from(at),
        )
    }

    private fun postingIds(snapshotIds: List<UUID>): Map<UUID, UUID> {
        if (snapshotIds.isEmpty()) return emptyMap()
        val placeholders = snapshotIds.distinct().joinToString(",") { "?" }
        return jdbc
            .query(
                "select id, posting_id from job_posting_snapshots where id in ($placeholders)",
                { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getObject("posting_id", UUID::class.java) },
                *snapshotIds.distinct().toTypedArray(),
            ).toMap()
    }

    private fun lastStatusChange(applicationIds: List<UUID>): Map<UUID, Instant> {
        if (applicationIds.isEmpty()) return emptyMap()
        val placeholders = applicationIds.joinToString(",") { "?" }
        return jdbc
            .query(
                "select application_id, max(occurred_at) as at from application_status_events where application_id in ($placeholders) group by application_id",
                { rs, _ -> rs.getObject("application_id", UUID::class.java) to rs.getTimestamp("at").toInstant() },
                *applicationIds.toTypedArray(),
            ).toMap()
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ApplicationEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun lock(
        workspace: WorkspaceContext,
        id: UUID,
    ): ApplicationEntity =
        repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private companion object {
        const val TARGET = "application"
        const val BOARD_LIMIT = 200
        val TERMINAL = ApplicationStatus.entries.filter { it.isTerminal }.map { it.name }
    }
}
