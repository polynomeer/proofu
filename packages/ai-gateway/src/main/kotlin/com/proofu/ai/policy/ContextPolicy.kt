package com.proofu.ai.policy

import com.proofu.ai.model.ContextDocument
import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.Sensitivity

/** Why a document was left out of the model context. */
enum class ExclusionReason {
    SENSITIVITY,
    TOKEN_BUDGET,
}

data class ContextDecision(
    val included: List<ContextDocument>,
    val excluded: Map<String, ExclusionReason>,
) {
    val includedSourceIds: Set<String> get() = included.map { it.sourceId }.toSet()
}

/**
 * Grounding policy steps 3–4: CONFIDENTIAL data enters a model context only with the user's
 * consent and RESTRICTED never does, and the context is capped so a request cannot
 * silently blow past the per-call token limit. Documents are kept in the caller's order and
 * dropped from the end when the budget runs out, so callers put the most relevant first.
 */
class ContextPolicy(
    private val maxInputTokens: Int,
) {
    fun apply(
        documents: List<ContextDocument>,
        consentToSensitive: Boolean,
        reservedTokens: Int,
    ): ContextDecision {
        val excluded = linkedMapOf<String, ExclusionReason>()
        val included = mutableListOf<ContextDocument>()
        var budget = maxInputTokens - reservedTokens
        for (doc in documents) {
            val consent = if (consentToSensitive) AiConsent.CONFIDENTIAL else AiConsent.NONE
            if (!doc.sensitivity.allowedInAiContext(consent)) {
                excluded[doc.sourceId] = ExclusionReason.SENSITIVITY
                continue
            }
            val cost = estimateTokens(doc.text) + estimateTokens(doc.title)
            if (cost > budget) {
                excluded[doc.sourceId] = ExclusionReason.TOKEN_BUDGET
                continue
            }
            budget -= cost
            included += doc
        }
        return ContextDecision(included, excluded)
    }

    companion object {
        /**
         * Conservative estimate for mixed Korean/English text (Korean runs ~1 token per 1.5
         * characters). Exact counts come from the provider's usage after the call.
         */
        fun estimateTokens(text: String): Int = (text.length / 1.5).toInt() + 1

        fun Sensitivity.blocksByDefault(): Boolean = !allowedInAiContextByDefault
    }
}
