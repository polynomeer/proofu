package com.proofu.domain.common

/** Normalised confidence in [0, 1]. Source quality and recency are recorded separately. */
@JvmInline
value class Confidence(
    val value: Double,
) {
    init {
        require(value in 0.0..1.0) { "confidence must be within [0, 1], was $value" }
    }

    companion object {
        val NONE = Confidence(0.0)
        val FULL = Confidence(1.0)
    }
}
