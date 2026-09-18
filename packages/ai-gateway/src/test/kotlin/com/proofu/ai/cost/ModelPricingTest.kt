package com.proofu.ai.cost

import com.proofu.ai.model.ModelUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModelPricingTest {
    @Test
    fun `opus 5 pricing in micro dollars`() {
        // 1M input at $5 = $5, 100K output at $25/M = $2.5, 500K cache reads at $0.5/M = $0.25
        val cost = ModelPricing.costMicros("claude-opus-5", ModelUsage(1_000_000, 100_000, 500_000, 0))
        assertThat(cost).isEqualTo(7_750_000)
    }

    @Test
    fun `unknown models are priced at the ceiling so budgets never under-count`() {
        val known = ModelPricing.costMicros("claude-opus-5", ModelUsage(1000, 1000, 0, 0))
        val unknown = ModelPricing.costMicros("claude-mystery-9", ModelUsage(1000, 1000, 0, 0))
        assertThat(unknown).isGreaterThanOrEqualTo(known)
    }
}
