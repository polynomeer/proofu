package com.proofu.domain.matching

import kotlin.math.roundToInt

enum class ScoreBand {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun of(score: Int): ScoreBand =
            when {
                score >= 70 -> HIGH
                score >= 40 -> MEDIUM
                else -> LOW
            }
    }
}

/** Sub-scores in [0, 1]. Each is shown to the user next to the evidence that produced it. */
data class MatchFeatures(
    val requirementCoverage: Double,
    val evidenceStrength: Double,
    val recency: Double,
    val complexity: Double,
    val impact: Double,
    val semanticSimilarity: Double,
) {
    init {
        listOf(requirementCoverage, evidenceStrength, recency, complexity, impact, semanticSimilarity)
            .forEach { require(it in 0.0..1.0) { "match feature must be within [0, 1], was $it" } }
    }
}

/**
 * Deterministic scoring (matching §10.2). The score is an ordering aid and is never
 * presented as a probability of acceptance; unmet REQUIRED requirements are surfaced
 * separately as gaps regardless of the number.
 */
data class MatchScore(
    val value: Int,
    val band: ScoreBand,
    val features: MatchFeatures,
) {
    companion object {
        const val W_REQUIREMENT_COVERAGE = 0.30
        const val W_EVIDENCE_STRENGTH = 0.20
        const val W_RECENCY = 0.15
        const val W_COMPLEXITY = 0.15
        const val W_IMPACT = 0.10
        const val W_SEMANTIC_SIMILARITY = 0.10

        fun compute(features: MatchFeatures): MatchScore {
            val raw =
                W_REQUIREMENT_COVERAGE * features.requirementCoverage +
                    W_EVIDENCE_STRENGTH * features.evidenceStrength +
                    W_RECENCY * features.recency +
                    W_COMPLEXITY * features.complexity +
                    W_IMPACT * features.impact +
                    W_SEMANTIC_SIMILARITY * features.semanticSimilarity
            val value = (raw * 100).roundToInt().coerceIn(0, 100)
            return MatchScore(value, ScoreBand.of(value), features)
        }
    }
}
