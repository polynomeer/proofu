package com.proofu.api.requirement

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** requirements has no workspace column; lookups join snapshot → posting to scope. */
interface RequirementRepository : JpaRepository<RequirementEntity, UUID> {
    @Query(
        """
        select r from RequirementEntity r, JobPostingSnapshotEntity s, JobPostingEntity p
        where r.id = :id and r.deletedAt is null and s.id = r.snapshotId and p.id = s.postingId
          and p.workspaceId = :workspaceId and p.deletedAt is null
        """,
    )
    fun findByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): RequirementEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select r from RequirementEntity r, JobPostingSnapshotEntity s, JobPostingEntity p
        where r.id = :id and r.deletedAt is null and s.id = r.snapshotId and p.id = s.postingId
          and p.workspaceId = :workspaceId and p.deletedAt is null
        """,
    )
    fun lockByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): RequirementEntity?

    fun findAllBySnapshotIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAscIdAsc(
        snapshotId: UUID,
    ): List<RequirementEntity>

    @Query("select coalesce(max(r.sortOrder), 0) from RequirementEntity r where r.snapshotId = :snapshotId")
    fun maxSortOrder(snapshotId: UUID): Int
}
