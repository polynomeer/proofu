package com.proofu.api.jobs

import com.proofu.domain.jobs.SnapshotSource
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Union of the contract's import variants; source-dependent requirements are checked in the service. */
data class ImportRequest(
    @field:NotNull val source: SnapshotSource?,
    val text: String? = null,
    @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val company: String? = null,
    @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val roleTitle: String? = null,
    @field:Size(max = 2048, message = "{max}자 이하로 입력하세요") val sourceUrl: String? = null,
    val publishedAt: Instant? = null,
    val url: String? = null,
    val externalPostingId: String? = null,
)

data class ImportResult(
    val postingId: UUID,
    val snapshotId: UUID,
    val contentHash: String,
    val capturedAt: Instant,
    val snapshotCreated: Boolean,
)

data class SnapshotSummary(
    val id: UUID,
    val source: SnapshotSource,
    val sourceUrl: String?,
    val contentHash: String,
    val capturedAt: Instant,
    val textPreview: String,
) {
    companion object {
        const val PREVIEW_LENGTH = 200

        fun from(s: JobPostingSnapshotEntity) =
            SnapshotSummary(
                id = s.id,
                source = s.source,
                sourceUrl = s.sourceUrl,
                contentHash = s.contentHash,
                capturedAt = s.capturedAt,
                textPreview = s.rawText.replace('\n', ' ').take(PREVIEW_LENGTH),
            )
    }
}

data class SnapshotResponse(
    val id: UUID,
    val postingId: UUID,
    val source: SnapshotSource,
    val sourceUrl: String?,
    val contentHash: String,
    val capturedAt: Instant,
    val textPreview: String,
    val rawText: String,
) {
    companion object {
        fun from(s: JobPostingSnapshotEntity): SnapshotResponse {
            val summary = SnapshotSummary.from(s)
            return SnapshotResponse(
                s.id,
                s.postingId,
                s.source,
                s.sourceUrl,
                s.contentHash,
                s.capturedAt,
                summary.textPreview,
                s.rawText,
            )
        }
    }
}

data class JobPostingResponse(
    val id: UUID,
    val company: String,
    val roleTitle: String,
    val canonicalUrl: String?,
    val externalPostingId: String?,
    val publishedAt: Instant?,
    val snapshotCount: Int,
    val latestSnapshot: SnapshotSummary?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            p: JobPostingEntity,
            snapshots: List<JobPostingSnapshotEntity>,
        ) = JobPostingResponse(
            id = p.id,
            company = p.company,
            roleTitle = p.roleTitle,
            canonicalUrl = p.canonicalUrl,
            externalPostingId = p.externalPostingId,
            publishedAt = p.publishedAt,
            snapshotCount = snapshots.size,
            latestSnapshot = snapshots.firstOrNull()?.let(SnapshotSummary::from),
            createdAt = checkNotNull(p.createdAt),
            updatedAt = checkNotNull(p.updatedAt),
        )
    }
}

data class JobPostingDetail(
    val id: UUID,
    val company: String,
    val roleTitle: String,
    val canonicalUrl: String?,
    val externalPostingId: String?,
    val publishedAt: Instant?,
    val snapshotCount: Int,
    val latestSnapshot: SnapshotSummary?,
    val snapshots: List<SnapshotSummary>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            p: JobPostingEntity,
            snapshots: List<JobPostingSnapshotEntity>,
        ): JobPostingDetail {
            val base = JobPostingResponse.from(p, snapshots)
            return JobPostingDetail(
                base.id,
                base.company,
                base.roleTitle,
                base.canonicalUrl,
                base.externalPostingId,
                base.publishedAt,
                base.snapshotCount,
                base.latestSnapshot,
                snapshots.map(SnapshotSummary::from),
                base.createdAt,
                base.updatedAt,
            )
        }
    }
}

data class JobPostingPage(
    val items: List<JobPostingResponse>,
    val nextCursor: String?,
)
