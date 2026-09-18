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

    /**
     * Phrases that state a rejection cause as settled fact. They do not block saving — the
     * product warns instead (docs/domain/application-lifecycle.md §서류 탈락 회고 모델).
     */
    fun definitiveLanguage(): List<String> =
        DEFINITIVE_PHRASES.filter { hypothesis.contains(it) || (rationale?.contains(it) ?: false) }

    companion object {
        val DEFINITIVE_PHRASES =
            listOf("때문에 탈락", "때문이다", "확실히", "분명히", "틀림없", "명백히", "탈락한 이유는", "떨어진 이유는", "원인은")
    }
}
