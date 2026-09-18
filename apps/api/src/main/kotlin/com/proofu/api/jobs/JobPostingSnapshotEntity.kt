package com.proofu.api.jobs

import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.SnapshotSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** Insert-only: the table's trigger rejects UPDATE and DELETE (ADR-0006), and Hibernate never tries. */
@Entity
@Immutable
@Table(name = "job_posting_snapshots")
class JobPostingSnapshotEntity(
    @Id
    val id: UUID,
    @Column(name = "posting_id")
    val postingId: UUID,
    @Enumerated(EnumType.STRING)
    val source: SnapshotSource,
    @Column(name = "source_url", columnDefinition = "text")
    val sourceUrl: String?,
    @Column(name = "raw_text", columnDefinition = "text")
    val rawText: String,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64)
    val contentHash: String,
    @Column(name = "captured_at")
    val capturedAt: Instant,
) {
    fun toDomain() =
        JobPostingSnapshot(
            JobPostingSnapshotId(id),
            JobPostingId(postingId),
            source,
            rawText,
            contentHash,
            capturedAt,
            sourceUrl,
        )

    companion object {
        fun from(s: JobPostingSnapshot) =
            JobPostingSnapshotEntity(
                s.id.value,
                s.postingId.value,
                s.source,
                s.sourceUrl,
                s.rawText,
                s.contentHash,
                s.capturedAt,
            )
    }
}
