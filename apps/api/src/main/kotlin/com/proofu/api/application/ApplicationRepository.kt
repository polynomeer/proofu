package com.proofu.api.application

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ApplicationRepository : JpaRepository<ApplicationEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): ApplicationEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select a from ApplicationEntity a where a.id = :id and a.workspaceId = :workspaceId and a.deletedAt is null",
    )
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): ApplicationEntity?

    /** Board order: most recently changed first. Terminal statuses can be dropped by the caller. */
    @Query(
        nativeQuery = true,
        value = """
            select * from applications
            where workspace_id = :workspaceId and deleted_at is null
              and (cast(:status as varchar) is null or status = cast(:status as varchar))
              and (:includeTerminal or status not in (:terminal))
            order by updated_at desc, id desc
            limit :limit
            """,
    )
    fun board(
        workspaceId: UUID,
        status: String?,
        includeTerminal: Boolean,
        terminal: List<String>,
        limit: Int,
    ): List<ApplicationEntity>
}
