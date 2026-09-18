package com.proofu.domain.jobs

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.common.domainRequire
import java.time.Instant

enum class RequirementCategory {
    REQUIRED,
    PREFERRED,
    RESPONSIBILITY,
    SKILL,
    BEHAVIORAL,
}

/** AI-extracted requirements start as DRAFT and are only usable for matching once APPROVED. */
enum class RequirementStatus {
    DRAFT,
    APPROVED,
    REJECTED,
}

/** Who wrote the requirement text. Manual entries are the user's own words and start approved. */
enum class RequirementOrigin {
    USER,
    AI,
}

/** Character range in the snapshot's raw text that the requirement was taken from. */
data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0 && end > start) { "invalid source span [$start, $end)" }
    }

    fun fitsIn(text: String): Boolean = end <= text.length
}

data class Requirement(
    val id: RequirementId,
    val snapshotId: JobPostingSnapshotId,
    val category: RequirementCategory,
    val text: String,
    val confidence: Confidence,
    val origin: RequirementOrigin,
    val sourceSpan: SourceSpan? = null,
    val status: RequirementStatus = RequirementStatus.DRAFT,
    val approvedAt: Instant? = null,
) {
    init {
        domainRequire(text.isNotBlank()) { "requirement text must not be blank" }
        domainRequire((status == RequirementStatus.APPROVED) == (approvedAt != null)) {
            "approvedAt must be set exactly when status is APPROVED"
        }
    }

    val usableForMatching: Boolean get() = status == RequirementStatus.APPROVED

    fun approve(at: Instant): Requirement = copy(status = RequirementStatus.APPROVED, approvedAt = at)

    fun reject(): Requirement = copy(status = RequirementStatus.REJECTED, approvedAt = null)

    /** Edited wording keeps the status: the user is rewriting, not re-deciding. */
    fun reworded(
        category: RequirementCategory,
        text: String,
        sourceSpan: SourceSpan?,
    ): Requirement = copy(category = category, text = text, sourceSpan = sourceSpan)

    companion object {
        /** The user typed it, so it is approved as written; confidence is full by definition. */
        fun manual(
            id: RequirementId,
            snapshotId: JobPostingSnapshotId,
            category: RequirementCategory,
            text: String,
            sourceSpan: SourceSpan?,
            at: Instant,
        ): Requirement =
            Requirement(
                id = id,
                snapshotId = snapshotId,
                category = category,
                text = text.trim(),
                confidence = Confidence.FULL,
                origin = RequirementOrigin.USER,
                sourceSpan = sourceSpan,
                status = RequirementStatus.APPROVED,
                approvedAt = at,
            )

        /** Model output waits for the user; nothing extracted is usable for matching until approved. */
        fun extracted(
            id: RequirementId,
            snapshotId: JobPostingSnapshotId,
            category: RequirementCategory,
            text: String,
            confidence: Confidence,
            sourceSpan: SourceSpan?,
        ): Requirement =
            Requirement(
                id = id,
                snapshotId = snapshotId,
                category = category,
                text = text.trim(),
                confidence = confidence,
                origin = RequirementOrigin.AI,
                sourceSpan = sourceSpan,
                status = RequirementStatus.DRAFT,
            )
    }
}
