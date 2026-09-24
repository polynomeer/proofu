package com.proofu.api.skill

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SkillRepository : JpaRepository<SkillEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): SkillEntity?

    fun findByWorkspaceIdAndIdInAndDeletedAtIsNull(
        workspaceId: UUID,
        ids: Collection<UUID>,
    ): List<SkillEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SkillEntity s where s.id = :id and s.workspaceId = :workspaceId and s.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): SkillEntity?

    /** Keyset page over (created_at date desc, id desc), optionally narrowed to one category. */
    @Query(
        nativeQuery = true,
        value = """
            select * from skills
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
    ): List<SkillEntity>

    /**
     * Skills of this workspace whose name or any alias collides with [names] (already normalized),
     * so a duplicate is reported before the unique index fires.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from skills
            where workspace_id = :workspaceId and deleted_at is null and (cast(:excludeId as uuid) is null or id <> cast(:excludeId as uuid))
              and exists (
                select 1 from unnest(array_append(aliases, canonical_name)) as n
                where lower(regexp_replace(btrim(n), '\s+', ' ', 'g')) = any(cast(:names as text[]))
              )
            """,
    )
    fun collisions(
        workspaceId: UUID,
        names: Array<String>,
        excludeId: UUID?,
    ): List<SkillEntity>
}
