package com.proofu.api.capability

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
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
              and (cast(:cursorDate as date) is null
                   or cast(created_at as date) < cast(:cursorDate as date)
                   or (cast(created_at as date) = cast(:cursorDate as date) and id < cast(:cursorId as uuid)))
            order by cast(created_at as date) desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        category: String?,
        cursorDate: LocalDate?,
        cursorId: UUID?,
        limit: Int,
    ): List<CapabilityEntity>
}
