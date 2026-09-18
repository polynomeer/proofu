package com.proofu.api.evidence

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import com.proofu.domain.evidence.EvidenceSource
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class EvidenceService(
    private val repository: EvidenceRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        type: EvidenceType?,
        verification: VerificationStatus?,
        q: String?,
        cursor: DateIdCursor?,
        limit: Int,
    ): EvidencePage {
        val rows =
            repository.page(
                workspace.workspaceId.value,
                type?.name,
                verification?.name,
                q?.trim()?.ifEmpty { null },
                cursor?.date,
                cursor?.id,
                limit + 1,
            )
        val page = rows.take(limit)
        val next =
            if (rows.size > limit) {
                val last = page.last()
                DateIdCursor(last.capturedAt.atZone(ZoneOffset.UTC).toLocalDate(), last.id).encode()
            } else {
                null
            }
        val counts = linkedClaimCounts(page.map { it.id })
        return EvidencePage(page.map { EvidenceResponse.from(it, counts[it.id] ?: 0) }, next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): EvidenceResponse =
        find(workspace, id).let {
            EvidenceResponse.from(
                it,
                linkedClaimCounts(listOf(it.id))[it.id] ?: 0,
            )
        }

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: EvidenceRequest,
    ): EvidenceResponse {
        if (request.type == EvidenceType.FILE) {
            throw DomainRuleViolation(
                "file evidence is registered through upload sessions, which are not available yet",
            )
        }
        val verification = request.verification ?: VerificationStatus.UNVERIFIED
        val evidence =
            request
                .toDomain(
                    id = EvidenceId(ids.next()),
                    workspaceId = workspace.workspaceId,
                    source = EvidenceSource.USER_INPUT,
                    capturedAtDefault = Instant.now(clock),
                    verification = VerificationStatus.UNVERIFIED,
                    confidence = Confidence.NONE,
                    revision = Revision.INITIAL,
                ).verifiedByUser(verification)
        val saved = repository.saveAndFlush(EvidenceEntity.from(evidence))
        val response = EvidenceResponse.from(saved, 0)
        audit.record(workspace, "evidence.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: EvidenceRequest,
        expectedRevision: Long,
    ): EvidenceResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        if (request.type != entity.type && (request.type == EvidenceType.FILE || entity.type == EvidenceType.FILE)) {
            throw DomainRuleViolation("evidence cannot change to or from the FILE type")
        }
        val before = EvidenceResponse.from(entity, 0)
        val next =
            request
                .toDomain(
                    id = EvidenceId(entity.id),
                    workspaceId = workspace.workspaceId,
                    source = entity.source,
                    capturedAtDefault = entity.capturedAt,
                    verification = entity.verification,
                    confidence = Confidence(entity.confidence.toDouble()),
                    revision = Revision(entity.revision).next(),
                ).let { if (request.verification != null) it.verifiedByUser(request.verification) else it }
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        val count = linkedClaimCounts(listOf(saved.id))[saved.id] ?: 0
        val response = EvidenceResponse.from(saved, count)
        audit.record(workspace, "evidence.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete. Links are removed so affected claims fall back to UNSUPPORTED. */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = EvidenceResponse.from(entity, 0)
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        jdbc.update("delete from claim_evidence where evidence_id = ?", entity.id)
        audit.record(workspace, "evidence.deleted", TARGET, entity.id, before = before)
    }

    fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): EvidenceEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun linkedClaimCounts(evidenceIds: List<UUID>): Map<UUID, Int> {
        if (evidenceIds.isEmpty()) return emptyMap()
        val placeholders = evidenceIds.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select ce.evidence_id, count(distinct ce.claim_id) as n
                from claim_evidence ce join claims c on c.id = ce.claim_id
                where ce.evidence_id in ($placeholders) and c.deleted_at is null
                group by ce.evidence_id
                """.trimIndent(),
                { rs, _ -> rs.getObject("evidence_id", UUID::class.java) to rs.getInt("n") },
                *evidenceIds.toTypedArray(),
            ).toMap()
    }

    private companion object {
        const val TARGET = "evidence"
    }
}
