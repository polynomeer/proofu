package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.ReviewId
import com.proofu.domain.common.domainRequire

enum class ReviewConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

/** Post-result retrospective. Facts and hypotheses are separate fields on purpose. */
data class Review(
    val id: ReviewId,
    val applicationId: ApplicationId,
    val observedFact: String,
    val hypothesis: String,
    val rationale: String? = null,
    val confidence: ReviewConfidence = ReviewConfidence.LOW,
    val improvementAction: String? = null,
    val verificationPlan: String? = null,
) {
    init {
        domainRequire(observedFact.isNotBlank()) { "review must record an observed fact" }
        domainRequire(hypothesis.isNotBlank()) { "review must record a hypothesis" }
    }
}
