package com.proofu.api.capability

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CapabilityRepository : JpaRepository<CapabilityEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): CapabilityEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CapabilityEntity c where c.id = :id and c.workspaceId = :workspaceId and c.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): CapabilityEntity?

    /** Keyset page over (created_at date desc, id desc), optionally narrowed to one category. */
    @Query(
        nativeQuery = true,
        value = """
            select * from capabilities
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:category as text) is null or category = cast(:category as text))
              and (cast(:cursorAt as timestamptz) is null
                   or (created_at, id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by created_at desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        category: String?,
        cursorAt: Instant?,
        cursorId: UUID?,
        limit: Int,
    ): List<CapabilityEntity>
}
