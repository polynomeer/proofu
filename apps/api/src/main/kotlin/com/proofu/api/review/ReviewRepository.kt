package com.proofu.api.review

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** reviews has no workspace column; every lookup joins through the (live) application. */
interface ReviewRepository : JpaRepository<ReviewEntity, UUID> {
    @Query(
        """
        select r from ReviewEntity r, ApplicationEntity a
        where r.id = :id and a.id = r.applicationId and a.workspaceId = :workspaceId and a.deletedAt is null
        """,
    )
    fun findByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): ReviewEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select r from ReviewEntity r, ApplicationEntity a
        where r.id = :id and a.id = r.applicationId and a.workspaceId = :workspaceId and a.deletedAt is null
        """,
    )
    fun lockByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): ReviewEntity?

    fun findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId: UUID): List<ReviewEntity>
}
