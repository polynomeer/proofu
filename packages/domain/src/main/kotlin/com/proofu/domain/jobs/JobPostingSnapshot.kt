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

    private companion object {
        val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
