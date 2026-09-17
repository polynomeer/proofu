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

/** Character range in the snapshot's raw text that the requirement was extracted from. */
data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0 && end > start) { "invalid source span [$start, $end)" }
    }
}

data class Requirement(
    val id: RequirementId,
    val snapshotId: JobPostingSnapshotId,
    val category: RequirementCategory,
    val text: String,
    val confidence: Confidence,
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
}
