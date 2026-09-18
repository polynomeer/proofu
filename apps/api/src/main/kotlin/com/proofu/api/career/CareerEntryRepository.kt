package com.proofu.api.career

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface CareerEntryRepository : JpaRepository<CareerEntryEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): CareerEntryEntity?

    /** Row lock for read-modify-write so the revision check cannot race another writer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select e from CareerEntryEntity e where e.id = :id and e.workspaceId = :workspaceId and e.deletedAt is null",
    )
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): CareerEntryEntity?

    /**
     * Keyset page ordered by (start_date desc, id desc). Full-text search uses the GIN
     * index from V1; explicit casts keep PostgreSQL happy with null parameters.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from career_entries
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:type as varchar) is null or type = cast(:type as varchar))
              and (cast(:q as varchar) is null
                   or to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))
                      @@ plainto_tsquery('simple', cast(:q as varchar)))
              and (cast(:cursorDate as date) is null
                   or start_date < cast(:cursorDate as date)
                   or (start_date = cast(:cursorDate as date) and id < cast(:cursorId as uuid)))
            order by start_date desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        type: String?,
        q: String?,
        cursorDate: LocalDate?,
        cursorId: UUID?,
        limit: Int,
    ): List<CareerEntryEntity>
}
