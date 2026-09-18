package com.proofu.api.requirement

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.jobs.JobPostingSnapshotEntity
import com.proofu.api.jobs.JobPostingSnapshotRepository
import com.proofu.api.web.InvalidRequestException
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.Requirement
import com.proofu.domain.jobs.RequirementStatus
import com.proofu.domain.jobs.SourceSpan
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class RequirementService(
    private val repository: RequirementRepository,
    private val snapshots: JobPostingSnapshotRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun listForSnapshot(
        workspace: WorkspaceContext,
        snapshotId: UUID,
    ): RequirementList {
        val snapshot = snapshot(workspace, snapshotId)
        val rows = repository.findAllBySnapshotIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAscIdAsc(snapshotId)
        return RequirementList(rows.map { response(it, snapshot) })
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): RequirementResponse {
        val entity =
            repository.findByIdInWorkspace(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        return response(entity, snapshots.findById(entity.snapshotId).orElseThrow().toDomain())
    }

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        snapshotId: UUID,
        request: CreateRequirementRequest,
    ): RequirementResponse {
        val snapshot = snapshot(workspace, snapshotId)
        val span = request.sourceSpan?.toDomain()?.also { snapshot.excerpt(it) }
        val requirement =
            Requirement.manual(
                id = RequirementId(ids.next()),
                snapshotId = JobPostingSnapshotId(snapshotId),
                category = requireNotNull(request.category),
                text = requireNotNull(request.text),
                sourceSpan = span,
                at = Instant.now(clock),
            )
        val saved =
            repository.saveAndFlush(
                RequirementEntity.from(requirement, repository.maxSortOrder(snapshotId) + 1),
            )
        val response = response(saved, snapshot)
        audit.record(workspace, "requirement.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: UpdateRequirementRequest,
    ): RequirementResponse {
        val entity =
            repository.lockByIdInWorkspace(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.version !=
            requireNotNull(request.version)
        ) {
            throw StaleVersionException(TARGET, request.version, entity.version)
        }
        val snapshot = snapshots.findById(entity.snapshotId).orElseThrow().toDomain()
        val before = response(entity, snapshot)

        var next = entity.toDomain()
        val span: SourceSpan? =
            when {
                request.clearSpan -> null
                request.sourceSpan != null -> request.sourceSpan.toDomain().also { snapshot.excerpt(it) }
                else -> next.sourceSpan
            }
        next = next.reworded(request.category ?: next.category, request.text?.trim() ?: next.text, span)
        next =
            when (request.status) {
                null -> next
                RequirementStatus.APPROVED -> next.approve(Instant.now(clock))
                RequirementStatus.REJECTED -> next.reject()
                RequirementStatus.DRAFT -> throw InvalidRequestException(
                    "DRAFT is reserved for AI extraction",
                    mapOf(
                        "status" to "허용되지 않는 값입니다",
                    ),
                )
            }
        if (next.text.isBlank()) throw DomainRuleViolation("requirement text must not be blank")
        entity.apply(next)
        entity.version += 1
        val saved = repository.saveAndFlush(entity)
        val response = response(saved, snapshot)
        audit.record(workspace, "requirement.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdInWorkspace(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = response(entity, snapshots.findById(entity.snapshotId).orElseThrow().toDomain())
        entity.deletedAt = Instant.now(clock)
        entity.version += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "requirement.deleted", TARGET, entity.id, before = before)
    }

    private fun snapshot(
        workspace: WorkspaceContext,
        id: UUID,
    ): JobPostingSnapshot =
        snapshots.findByIdInWorkspace(id, workspace.workspaceId.value)?.let(JobPostingSnapshotEntity::toDomain)
            ?: throw ResourceNotFoundException("job_posting_snapshot", id)

    private fun response(
        entity: RequirementEntity,
        snapshot: JobPostingSnapshot,
    ): RequirementResponse =
        RequirementResponse.from(
            entity,
            entity.sourceSpan
                ?.takeIf {
                    it.fitsIn(snapshot.rawText)
                }?.let(snapshot::excerpt),
        )

    private companion object {
        const val TARGET = "requirement"
    }
}
