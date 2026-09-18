package com.proofu.ai.cost

import com.proofu.ai.model.ModelUsage

/** USD per million tokens. Cache reads are ~0.1× input, cache writes ~1.25× input. */
data class Price(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
) {
    val cacheReadPerMillion: Double get() = inputPerMillion * 0.1
    val cacheWritePerMillion: Double get() = inputPerMillion * 1.25
}

/**
 * Anthropic first-party list prices (ADR-0008, cached 2026-06). Costs are recorded in
 * micro-dollars so budgets add up exactly. Unknown models are priced at the most
 * expensive known rate so a typo can never under-count.
 */
object ModelPricing {
    private val table =
        mapOf(
            "claude-opus-5" to Price(5.00, 25.00),
            "claude-sonnet-5" to Price(2.00, 10.00),
            "claude-haiku-4-5" to Price(1.00, 5.00),
        )
    private val ceiling = table.values.maxBy { it.outputPerMillion }

    fun priceOf(model: String): Price = table[model] ?: ceiling

    fun costMicros(
        model: String,
        usage: ModelUsage,
    ): Long {
        val p = priceOf(model)
        val usd =
            usage.inputTokens * p.inputPerMillion +
                usage.outputTokens * p.outputPerMillion +
                usage.cacheReadInputTokens * p.cacheReadPerMillion +
                usage.cacheWriteInputTokens * p.cacheWritePerMillion
        return Math.round(usd) // per-million rates × tokens already equals micro-dollars
    }
}
