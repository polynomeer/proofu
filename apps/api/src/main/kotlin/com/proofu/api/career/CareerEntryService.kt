package com.proofu.api.career

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.career.CareerEntryStatus
import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import com.proofu.domain.events.CareerEntryChanged
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class CareerEntryService(
    private val repository: CareerEntryRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        type: CareerEntryType?,
        q: String?,
        cursor: CareerEntryCursor?,
        limit: Int,
    ): CareerEntryPage {
        val rows =
            repository.page(
                workspaceId = workspace.workspaceId.value,
                type = type?.name,
                q = q?.trim()?.ifEmpty { null },
                cursorDate = cursor?.startDate,
                cursorId = cursor?.id,
                limit = limit + 1,
            )
        val page = rows.take(limit)
        val next = if (rows.size > limit) CareerEntryCursor.of(page.last()).encode() else null
        return CareerEntryPage(page.map(CareerEntryResponse::from), next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): CareerEntryResponse = CareerEntryResponse.from(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: CareerEntryRequest,
    ): CareerEntryResponse {
        val entry =
            request.toDomain(
                id = CareerEntryId(ids.next()),
                workspaceId = workspace.workspaceId,
                status = CareerEntryStatus.ACTIVE,
                revision = Revision.INITIAL,
            )
        val saved = repository.saveAndFlush(CareerEntryEntity.from(entry))
        val response = CareerEntryResponse.from(saved)
        audit.record(workspace, "career_entry.created", TARGET, saved.id, after = response)
        publishChanged(workspace, saved)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: CareerEntryRequest,
        expectedRevision: Long,
    ): CareerEntryResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val before = CareerEntryResponse.from(entity)
        val next =
            request.toDomain(
                id = CareerEntryId(entity.id),
                workspaceId = workspace.workspaceId,
                status = entity.status,
                revision = Revision(entity.revision).next(),
            )
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        val response = CareerEntryResponse.from(saved)
        audit.record(workspace, "career_entry.updated", TARGET, saved.id, before = before, after = response)
        publishChanged(workspace, saved)
        return response
    }

    /** Soft delete: the row stays for the 30-day trash window (docs/data/retention.md). */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = CareerEntryResponse.from(entity)
        entity.status = CareerEntryStatus.DELETED
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "career_entry.deleted", TARGET, entity.id, before = before)
        publishChanged(workspace, entity)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): CareerEntryEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun publishChanged(
        workspace: WorkspaceContext,
        entity: CareerEntryEntity,
    ) {
        events.publishEvent(
            CareerEntryChanged(
                eventId = ids.next(),
                occurredAt = Instant.now(clock),
                workspaceId = workspace.workspaceId,
                entityId = CareerEntryId(entity.id),
                revision = Revision(entity.revision),
            ),
        )
    }

    private companion object {
        const val TARGET = "career_entry"
    }
}
