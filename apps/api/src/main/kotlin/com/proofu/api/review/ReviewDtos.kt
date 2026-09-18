package com.proofu.api.review

import com.proofu.domain.applications.Review
import com.proofu.domain.applications.ReviewConfidence
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.ReviewId
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** Matches `ReviewInput`: fact and hypothesis are separate, required fields on purpose. */
data class ReviewRequest(
    @field:NotBlank val observedFact: String?,
    @field:NotBlank val hypothesis: String?,
    val rationale: String? = null,
    val confidence: ReviewConfidence? = null,
    val improvementAction: String? = null,
    val verificationPlan: String? = null,
    val version: Long? = null,
) {
    fun toDomain(
        id: ReviewId,
        applicationId: ApplicationId,
    ): Review =
        Review(
            id = id,
            applicationId = applicationId,
            observedFact = requireNotNull(observedFact).trim(),
            hypothesis = requireNotNull(hypothesis).trim(),
            rationale = rationale?.trim()?.ifEmpty { null },
            confidence = confidence ?: ReviewConfidence.LOW,
            improvementAction = improvementAction?.trim()?.ifEmpty { null },
            verificationPlan = verificationPlan?.trim()?.ifEmpty { null },
        )
}

data class ReviewResponse(
    val id: UUID,
    val applicationId: UUID,
    val observedFact: String,
    val hypothesis: String,
    val rationale: String?,
    val confidence: ReviewConfidence,
    val improvementAction: String?,
    val verificationPlan: String?,
    val warnings: List<String>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: ReviewEntity) =
            ReviewResponse(
                id = e.id,
                applicationId = e.applicationId,
                observedFact = e.observedFact,
                hypothesis = e.hypothesis,
                rationale = e.rationale,
                confidence = e.confidence,
                improvementAction = e.improvementAction,
                verificationPlan = e.verificationPlan,
                warnings = e.toDomain().definitiveLanguage(),
                version = e.version,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class ReviewList(
    val items: List<ReviewResponse>,
)
