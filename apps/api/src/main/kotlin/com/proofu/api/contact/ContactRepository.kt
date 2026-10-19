package com.proofu.api.contact

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface ContactRepository : JpaRepository<ContactEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): ContactEntity?

    /** Row lock for read-modify-write so the revision check cannot race another writer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContactEntity c where c.id = :id and c.workspaceId = :workspaceId and c.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): ContactEntity?

    /**
     * Keyset page over (created_at desc, id desc), optionally narrowed to one relation. Contacts
     * are not part of the global search (third-party data stays where it was entered), so this
     * list is the only way to find one.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from contacts
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:relation as text) is null or relation = cast(:relation as text))
              and (cast(:cursorAt as timestamptz) is null
                   or (created_at, id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by created_at desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        relation: String?,
        cursorAt: Instant?,
        cursorId: UUID?,
        limit: Int,
    ): List<ContactEntity>
}
