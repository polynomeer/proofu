package com.proofu.api.capability

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.InstantIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.career.CapabilityCategory
import com.proofu.domain.common.CapabilityId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import com.proofu.domain.evidence.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * F01 역량. The level stays the user's own judgement; this service only reports what evidence is
 * attached (docs/domain/career-data-model.md §역량). Nesting is checked against the live tree so
 * a capability never ends up under one of its own descendants.
 */
@Service
class CapabilityService(
    private val repository: CapabilityRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        category: CapabilityCategory?,
        cursor: InstantIdCursor?,
        limit: Int,
    ): CapabilityPage {
        val rows = repository.page(workspace.workspaceId.value, category?.name, cursor?.at, cursor?.id, limit + 1)
        val page = rows.take(limit)
        val evidence = evidenceOf(page.map { it.id })
        val next =
            if (rows.size > limit) {
                InstantIdCursor(checkNotNull(page.last().createdAt), page.last().id).encode()
            } else {
                null
            }
        return CapabilityPage(page.map { CapabilityResponse.from(it, evidence[it.id].orEmpty()) }, next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): CapabilityResponse = response(find(workspace, id))

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: CapabilityRequest,
    ): CapabilityResponse {
        val capability = request.toDomain(CapabilityId(ids.next()), workspace.workspaceId, Revision.INITIAL)
        requireParent(workspace, capability.parentId?.value, childId = null)
        val saved = repository.saveAndFlush(CapabilityEntity.from(capability))
        val response = response(saved)
        audit.record(workspace, "capability.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: CapabilityRequest,
        expectedRevision: Long,
    ): CapabilityResponse {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val before = response(entity)
        val next =
            entity.toDomain().revisedTo(
                request.toDomain(CapabilityId(entity.id), workspace.workspaceId, Revision(entity.revision)),
            )
        requireParent(workspace, next.parentId?.value, childId = entity.id)
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        val response = response(saved)
        audit.record(workspace, "capability.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete; children go to the trash with the parent and evidence links are dropped. */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = response(entity)
        val doomed = descendants(workspace, entity.id) + entity.id
        val now = Instant.now(clock)
        val placeholders = doomed.joinToString(",") { "?" }
        jdbc.update(
            "update capabilities set deleted_at = ?, revision = revision + 1 where id in ($placeholders) and deleted_at is null",
            java.sql.Timestamp.from(now),
            *doomed.toTypedArray(),
        )
        jdbc.update("delete from capability_evidence where capability_id in ($placeholders)", *doomed.toTypedArray())
        audit.record(workspace, "capability.deleted", TARGET, entity.id, before = before)
    }

    /** Replaces the whole set; evidence of another workspace is reported as not found. */
    @Transactional
    fun setEvidence(
        workspace: WorkspaceContext,
        id: UUID,
        request: CapabilityEvidenceRequest,
    ): CapabilityResponse {
        val entity = find(workspace, id)
        val wanted = requireNotNull(request.evidenceIds).distinct()
        if (wanted.isNotEmpty()) {
            val placeholders = wanted.joinToString(",") { "?" }
            val found =
                jdbc
                    .query(
                        "select id from evidence where workspace_id = ? and deleted_at is null and id in ($placeholders)",
                        { rs, _ -> rs.getObject("id", UUID::class.java) },
                        workspace.workspaceId.value,
                        *wanted.toTypedArray(),
                    ).toSet()
            val missing = wanted - found
            if (missing.isNotEmpty()) throw ResourceNotFoundException("evidence", missing.first())
        }
        val before = response(entity)
        jdbc.update("delete from capability_evidence where capability_id = ?", entity.id)
        wanted.forEach {
            jdbc.update(
                "insert into capability_evidence (capability_id, evidence_id) values (?, ?)",
                entity.id,
                it,
            )
        }
        val after = response(entity)
        audit.record(workspace, "capability.evidence_set", TARGET, entity.id, before = before, after = after)
        return after
    }

    private fun response(entity: CapabilityEntity): CapabilityResponse =
        CapabilityResponse.from(entity, evidenceOf(listOf(entity.id))[entity.id].orEmpty())

    private fun evidenceOf(capabilityIds: List<UUID>): Map<UUID, List<CapabilityEvidenceResponse>> {
        if (capabilityIds.isEmpty()) return emptyMap()
        val placeholders = capabilityIds.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select ce.capability_id, e.id, e.title, e.verification from capability_evidence ce
                join evidence e on e.id = ce.evidence_id
                where ce.capability_id in ($placeholders) and e.deleted_at is null
                order by e.title
                """.trimIndent(),
                { rs, _ ->
                    rs.getObject("capability_id", UUID::class.java) to
                        CapabilityEvidenceResponse(
                            id = rs.getObject("id", UUID::class.java),
                            title = rs.getString("title"),
                            verification = VerificationStatus.valueOf(rs.getString("verification")),
                        )
                },
                *capabilityIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): CapabilityEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    /** Live descendants of [id], deepest last; used to take a subtree to the trash at once. */
    private fun descendants(
        workspace: WorkspaceContext,
        id: UUID,
    ): List<UUID> =
        jdbc.query(
            """
            with recursive tree as (
                select id from capabilities where parent_id = ? and workspace_id = ? and deleted_at is null
                union all
                select c.id from capabilities c join tree t on c.parent_id = t.id
                where c.workspace_id = ? and c.deleted_at is null
            )
            select id from tree
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            id,
            workspace.workspaceId.value,
            workspace.workspaceId.value,
        )

    /** The parent must exist in this workspace and must not sit below [childId]. */
    private fun requireParent(
        workspace: WorkspaceContext,
        parentId: UUID?,
        childId: UUID?,
    ) {
        if (parentId == null) return
        find(workspace, parentId)
        if (childId != null && parentId in descendants(workspace, childId)) {
            throw DomainRuleViolation("a capability cannot be nested under one of its own descendants")
        }
    }

    private companion object {
        const val TARGET = "capability"
    }
}
