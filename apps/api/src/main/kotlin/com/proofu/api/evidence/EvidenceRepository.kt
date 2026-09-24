package com.proofu.api.evidence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface EvidenceRepository : JpaRepository<EvidenceEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): EvidenceEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EvidenceEntity e where e.id = :id and e.workspaceId = :workspaceId and e.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): EvidenceEntity?

    /** Keyset page by (capture date desc, id desc). */
    @Query(
        nativeQuery = true,
        value = """
            select * from evidence
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:type as varchar) is null or type = cast(:type as varchar))
              and (cast(:verification as varchar) is null or verification = cast(:verification as varchar))
              and (cast(:q as varchar) is null
                   or to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(body, ''))
                      @@ plainto_tsquery('simple', cast(:q as varchar)))
              and (cast(:cursorAt as timestamptz) is null
                   or (captured_at, id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by captured_at desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        type: String?,
        verification: String?,
        q: String?,
        cursorAt: Instant?,
        cursorId: UUID?,
        limit: Int,
    ): List<EvidenceEntity>
}
