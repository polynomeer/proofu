package com.proofu.api.jobs

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface JobPostingRepository : JpaRepository<JobPostingEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): JobPostingEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from JobPostingEntity p where p.id = :id and p.workspaceId = :workspaceId and p.deletedAt is null")
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): JobPostingEntity?

    /** Reuse target for manual imports that carry a url. Locked so two imports of one url serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select p from JobPostingEntity p where p.workspaceId = :workspaceId and p.canonicalUrl = :url and p.deletedAt is null",
    )
    fun lockByCanonicalUrl(
        workspaceId: UUID,
        url: String,
    ): JobPostingEntity?

    /** Keyset by (updated_at date desc, id desc); updated_at is touched whenever a snapshot is added. */
    @Query(
        nativeQuery = true,
        value = """
            select p.* from job_postings p
            where p.workspace_id = :workspaceId and p.deleted_at is null
              and (cast(:q as varchar) is null or exists (
                    select 1 from job_posting_snapshots s
                    where s.posting_id = p.id
                      and to_tsvector('simple', p.company || ' ' || p.role_title || ' ' || s.raw_text)
                          @@ plainto_tsquery('simple', cast(:q as varchar))))
              and (cast(:cursorDate as date) is null
                   or cast(p.updated_at as date) < cast(:cursorDate as date)
                   or (cast(p.updated_at as date) = cast(:cursorDate as date) and p.id < cast(:cursorId as uuid)))
            order by cast(p.updated_at as date) desc, p.id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        q: String?,
        cursorDate: LocalDate?,
        cursorId: UUID?,
        limit: Int,
    ): List<JobPostingEntity>
}

interface JobPostingSnapshotRepository : JpaRepository<JobPostingSnapshotEntity, UUID> {
    fun findByPostingIdAndContentHash(
        postingId: UUID,
        contentHash: String,
    ): JobPostingSnapshotEntity?

    fun findAllByPostingIdInOrderByCapturedAtDescIdDesc(postingIds: Collection<UUID>): List<JobPostingSnapshotEntity>

    @Query(
        """
        select s from JobPostingSnapshotEntity s, JobPostingEntity p
        where s.id = :id and p.id = s.postingId and p.workspaceId = :workspaceId and p.deletedAt is null
        """,
    )
    fun findByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): JobPostingSnapshotEntity?
}
