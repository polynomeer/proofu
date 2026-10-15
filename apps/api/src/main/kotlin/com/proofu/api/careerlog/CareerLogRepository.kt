package com.proofu.api.careerlog

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface CareerLogRepository : JpaRepository<CareerLogEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): CareerLogEntity?

    /** Row lock for read-modify-write so the revision check cannot race another writer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CareerLogEntity l where l.id = :id and l.workspaceId = :workspaceId and l.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): CareerLogEntity?

    /**
     * Keyset page ordered by (occurred_at desc, id desc) — when it happened, not when it was
     * typed. [promoted] narrows to the logs still waiting to become an achievement, which is the
     * one filter the screen offers and the one the partial index of V19 answers.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from career_logs
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:promoted as boolean) is null
                   or (cast(:promoted as boolean) and promoted_at is not null)
                   or (not cast(:promoted as boolean) and promoted_at is null))
              and (cast(:cursorDate as date) is null
                   or occurred_at < cast(:cursorDate as date)
                   or (occurred_at = cast(:cursorDate as date) and id < cast(:cursorId as uuid)))
            order by occurred_at desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        promoted: Boolean?,
        cursorDate: LocalDate?,
        cursorId: UUID?,
        limit: Int,
    ): List<CareerLogEntity>
}
