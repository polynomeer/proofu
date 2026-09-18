package com.proofu.api.claim

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ClaimRepository : JpaRepository<ClaimEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): ClaimEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClaimEntity c where c.id = :id and c.workspaceId = :workspaceId and c.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): ClaimEntity?

    @Query(
        nativeQuery = true,
        value = """
            select distinct c.* from claims c
            join claim_sources cs on cs.claim_id = c.id
            where c.workspace_id = :workspaceId and c.deleted_at is null
              and cs.source_type = :sourceType and cs.source_id = :sourceId
            order by c.created_at, c.id
            """,
    )
    fun findBySource(
        workspaceId: UUID,
        sourceType: String,
        sourceId: UUID,
    ): List<ClaimEntity>

    /** Claims about a project or any of its (live) achievements. */
    @Query(
        nativeQuery = true,
        value = """
            select distinct c.* from claims c
            join claim_sources cs on cs.claim_id = c.id
            where c.workspace_id = :workspaceId and c.deleted_at is null
              and ((cs.source_type = 'PROJECT' and cs.source_id = :projectId)
                   or (cs.source_type = 'ACHIEVEMENT'
                       and cs.source_id in (select id from achievements where project_id = :projectId and deleted_at is null)))
            order by c.created_at, c.id
            """,
    )
    fun findByProject(
        workspaceId: UUID,
        projectId: UUID,
    ): List<ClaimEntity>

    @Query(
        nativeQuery = true,
        value = """
            select distinct c.* from claims c
            join claim_evidence ce on ce.claim_id = c.id
            where c.workspace_id = :workspaceId and c.deleted_at is null and ce.evidence_id = :evidenceId
            order by c.created_at, c.id
            """,
    )
    fun findByEvidence(
        workspaceId: UUID,
        evidenceId: UUID,
    ): List<ClaimEntity>

    @Query(
        nativeQuery = true,
        value = """
            select * from claims where workspace_id = :workspaceId and deleted_at is null
            order by created_at, id limit :limit
            """,
    )
    fun findAllInWorkspace(
        workspaceId: UUID,
        limit: Int,
    ): List<ClaimEntity>
}
