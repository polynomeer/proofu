package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.SubmissionSnapshotId
import com.proofu.domain.common.UnapprovedBlocksInExport
import com.proofu.domain.common.domainRequire
import com.proofu.domain.documents.GeneratedOutput
import java.security.MessageDigest
import java.time.Duration
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

    companion object {
        /** Clock skew tolerated for "submitted just now". */
        val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

        /**
         * The only way to make a snapshot. The version must belong to a document of this
         * application, every INFERRED/UNSUPPORTED block must be approved ([UnapprovedBlocksInExport]),
         * the application must still accept a submission and the time must not be in the future.
         */
        fun freeze(
            id: SubmissionSnapshotId,
            application: Application,
            documentVersionId: DocumentVersionId,
            documentApplicationId: ApplicationId,
            content: GeneratedOutput,
            contentJson: String,
            postingContentHash: String,
            submittedAt: Instant,
            now: Instant,
        ): SubmissionSnapshot {
            domainRequire(documentApplicationId == application.id) {
                "document version ${documentVersionId.value} belongs to another application"
            }
            domainRequire(application.status.acceptsSubmission) {
                "application in status ${application.status} no longer accepts a submission"
            }
            domainRequire(!submittedAt.isAfter(now.plus(FUTURE_TOLERANCE))) { "submittedAt must not be in the future" }
            val pending = content.blocksPendingApproval()
            if (pending.isNotEmpty()) throw UnapprovedBlocksInExport(pending.map { it.blockId })
            return SubmissionSnapshot(
                id = id,
                applicationId = application.id,
                documentVersionId = documentVersionId,
                postingSnapshotId = application.snapshotId,
                hash = hashOf(documentVersionId, contentJson, postingContentHash),
                submittedAt = submittedAt,
            )
        }

        /** Lowercase SHA-256 hex over `versionId \n content \n postingHash`; exact bytes, no normalisation. */
        fun hashOf(
            documentVersionId: DocumentVersionId,
            contentJson: String,
            postingContentHash: String,
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${documentVersionId.value}\n$contentJson\n$postingContentHash".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
