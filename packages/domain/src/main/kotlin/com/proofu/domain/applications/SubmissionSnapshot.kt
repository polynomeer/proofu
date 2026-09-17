package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.SubmissionSnapshotId
import java.time.Instant

/**
 * Immutable bundle of what was actually submitted (ADR-0006). There is deliberately no
 * copy-style mutation API: corrections create a new snapshot.
 */
class SubmissionSnapshot(
    val id: SubmissionSnapshotId,
    val applicationId: ApplicationId,
    val documentVersionId: DocumentVersionId,
    val postingSnapshotId: JobPostingSnapshotId,
    /** sha256 over the frozen document content and posting snapshot hash. */
    val hash: String,
    val submittedAt: Instant,
) {
    override fun equals(other: Any?): Boolean = other is SubmissionSnapshot && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "SubmissionSnapshot(id=${id.value}, submittedAt=$submittedAt)"
}
