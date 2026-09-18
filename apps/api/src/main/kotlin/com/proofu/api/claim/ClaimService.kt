package com.proofu.api.claim

import com.proofu.api.audit.AuditLog
import com.proofu.api.evidence.EvidenceService
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.evidence.Claim
import com.proofu.domain.evidence.ClaimEvidenceLink
import com.proofu.domain.evidence.ClaimSource
import com.proofu.domain.evidence.ClaimSourceType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ClaimService(
    private val repository: ClaimRepository,
    private val assembler: ClaimAssembler,
    private val evidenceService: EvidenceService,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        sourceType: ClaimSourceType?,
        sourceId: UUID?,
        projectId: UUID?,
    ): ClaimList {
        val ws = workspace.workspaceId.value
        val rows =
            when {
                projectId != null -> repository.findByProject(ws, projectId)
                sourceType != null && sourceId != null -> repository.findBySource(ws, sourceType.name, sourceId)
                else -> repository.findAllInWorkspace(ws, MAX_UNFILTERED)
            }
        return ClaimList(assembler.assemble(rows))
    }

    @Transactional(readOnly = true)
    fun listForEvidence(
        workspace: WorkspaceContext,
        evidenceId: UUID,
    ): ClaimList {
        evidenceService.find(workspace, evidenceId)
        return ClaimList(assembler.assemble(repository.findByEvidence(workspace.workspaceId.value, evidenceId)))
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ClaimResponse = assembler.assemble(listOf(find(workspace, id))).single()

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: ClaimRequest,
    ): ClaimResponse {
        val sources = request.sources.map { pinSource(workspace, requireNotNull(it.type), requireNotNull(it.id)) }
        val claim =
            Claim(
                id = ClaimId(ids.next()),
                workspaceId = workspace.workspaceId,
                text = requireNotNull(request.text).trim(),
                type = requireNotNull(request.type),
                sensitivity = request.sensitivity ?: Sensitivity.INTERNAL,
                sources = sources,
            )
        val saved = repository.saveAndFlush(ClaimEntity.from(claim))
        sources.forEach {
            jdbc.update(
                "insert into claim_sources (claim_id, source_type, source_id, source_revision) values (?, ?, ?, ?)",
                saved.id,
                it.type.name,
                it.id,
                it.revision.value,
            )
        }
        val response = get(workspace, saved.id)
        audit.record(workspace, "claim.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: ClaimRequest,
        expectedRevision: Long,
    ): ClaimResponse {
        val entity = lock(workspace, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val before = assembler.assemble(listOf(entity)).single()
        val next =
            entity.toDomain(assembler.sourcesOf(entity.id)).copy(
                text = requireNotNull(request.text).trim(),
                type = requireNotNull(request.type),
                sensitivity = request.sensitivity ?: entity.sensitivity,
                revision = Revision(entity.revision).next(),
            )
        entity.apply(next)
        repository.saveAndFlush(entity)
        val response = get(workspace, entity.id)
        audit.record(workspace, "claim.updated", TARGET, entity.id, before = before, after = response)
        return response
    }

    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity = lock(workspace, id)
        val before = assembler.assemble(listOf(entity)).single()
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "claim.deleted", TARGET, entity.id, before = before)
    }

    /** Upserts the (claim, evidence, relation) link; the domain checks workspace and scope rules. */
    @Transactional
    fun link(
        workspace: WorkspaceContext,
        claimId: UUID,
        request: LinkEvidenceRequest,
    ): ClaimResponse {
        val claimEntity = lock(workspace, claimId)
        val evidence = evidenceService.find(workspace, requireNotNull(request.evidenceId)).toDomain()
        val link =
            ClaimEvidenceLink.link(
                claimEntity.toDomain(assembler.sourcesOf(claimEntity.id)),
                evidence,
                requireNotNull(request.relation),
                Confidence(requireNotNull(request.confidence)),
                request.scope?.trim()?.ifEmpty { null },
            )
        jdbc.update(
            """
            insert into claim_evidence (claim_id, evidence_id, relation, scope, confidence) values (?, ?, ?, ?, ?)
            on conflict (claim_id, evidence_id, relation) do update set scope = excluded.scope, confidence = excluded.confidence
            """.trimIndent(),
            link.claimId.value,
            link.evidenceId.value,
            link.relation.name,
            link.scope,
            BigDecimal.valueOf(link.confidence.value).setScale(2, RoundingMode.HALF_UP),
        )
        val response = get(workspace, claimId)
        audit.record(workspace, "claim.evidence_linked", TARGET, claimId, after = response)
        return response
    }

    @Transactional
    fun unlink(
        workspace: WorkspaceContext,
        claimId: UUID,
        evidenceId: UUID,
    ): ClaimResponse {
        lock(workspace, claimId)
        val removed =
            jdbc.update(
                "delete from claim_evidence where claim_id = ? and evidence_id = ?",
                claimId,
                evidenceId,
            )
        if (removed == 0) throw ResourceNotFoundException("claim_evidence", evidenceId)
        val response = get(workspace, claimId)
        audit.record(workspace, "claim.evidence_unlinked", TARGET, claimId, after = response)
        return response
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ClaimEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun lock(
        workspace: WorkspaceContext,
        id: UUID,
    ): ClaimEntity =
        repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    /** Reads the record's current revision inside the workspace; other workspaces' records are "not found". */
    private fun pinSource(
        workspace: WorkspaceContext,
        type: ClaimSourceType,
        id: UUID,
    ): ClaimSource {
        val table =
            when (type) {
                ClaimSourceType.CAREER_ENTRY -> "career_entries"
                ClaimSourceType.PROJECT -> "projects"
                ClaimSourceType.ACHIEVEMENT -> "achievements"
            }
        val revision =
            jdbc
                .query(
                    "select revision from $table where id = ? and workspace_id = ? and deleted_at is null",
                    { rs, _ -> rs.getLong(1) },
                    id,
                    workspace.workspaceId.value,
                ).firstOrNull() ?: throw ResourceNotFoundException(type.name.lowercase(), id)
        return ClaimSource(type, id, Revision(revision))
    }

    private companion object {
        const val TARGET = "claim"
        const val MAX_UNFILTERED = 200
    }
}
