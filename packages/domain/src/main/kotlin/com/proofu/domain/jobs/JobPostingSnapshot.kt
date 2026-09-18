package com.proofu.domain.jobs

import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.domainRequire
import java.time.Instant

enum class SnapshotSource {
    MANUAL_TEXT,
    URL_FETCH,
    CAREER_OPS,
}

/** Immutable capture of a posting at a point in time. A changed contentHash yields a new snapshot. */
data class JobPostingSnapshot(
    val id: JobPostingSnapshotId,
    val postingId: JobPostingId,
    val source: SnapshotSource,
    val rawText: String,
    val contentHash: String,
    val capturedAt: Instant,
    val sourceUrl: String? = null,
) {
    init {
        domainRequire(rawText.isNotBlank()) { "snapshot raw text must not be blank" }
        domainRequire(contentHash.matches(SHA256_HEX)) { "content hash must be lowercase sha256 hex" }
    }

    /** The captured text a requirement points at; a span outside the text is a rule violation. */
    fun excerpt(span: SourceSpan): String {
        domainRequire(
            span.fitsIn(rawText),
        ) { "source span ${span.start}-${span.end} exceeds text length ${rawText.length}" }
        return rawText.substring(span.start, span.end)
    }

    companion object {
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        /** Captures normalised text with its hash; the only way new snapshots should be made. */
        fun capture(
            id: JobPostingSnapshotId,
            postingId: JobPostingId,
            source: SnapshotSource,
            text: String,
            capturedAt: Instant,
            sourceUrl: String? = null,
        ): JobPostingSnapshot {
            val normalized = ContentHash.normalize(text)
            return JobPostingSnapshot(
                id,
                postingId,
                source,
                normalized,
                ContentHash.of(normalized),
                capturedAt,
                sourceUrl,
            )
        }
    }
}
