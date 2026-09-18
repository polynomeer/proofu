package com.proofu.api.project

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface ProjectRepository : JpaRepository<ProjectEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): ProjectEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProjectEntity p where p.id = :id and p.workspaceId = :workspaceId and p.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): ProjectEntity?

    /**
     * Keyset page ordered by (sort_date desc, id desc) where projects without a start date
     * sort first as "ongoing/unknown". The cursor carries the coalesced sort date.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from projects
            where workspace_id = :workspaceId
              and deleted_at is null
              and (cast(:careerEntryId as uuid) is null or career_entry_id = cast(:careerEntryId as uuid))
              and (cast(:cursorDate as date) is null
                   or coalesce(start_date, date '9999-12-31') < cast(:cursorDate as date)
                   or (coalesce(start_date, date '9999-12-31') = cast(:cursorDate as date) and id < cast(:cursorId as uuid)))
            order by coalesce(start_date, date '9999-12-31') desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        careerEntryId: UUID?,
        cursorDate: LocalDate?,
        cursorId: UUID?,
        limit: Int,
    ): List<ProjectEntity>
}
