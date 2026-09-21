package com.proofu.worker.jobs

import com.proofu.ai.AiBudgetExceeded
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.BudgetBlock
import com.proofu.ai.model.ModelProviderException
import com.proofu.ai.model.ProviderFailure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AiFailuresTest {
    @Test
    fun `provider failures map to distinct codes with the right retry policy`() {
        val billing = AiFailures.fromProvider(ModelProviderException("credit", failure = ProviderFailure.BILLING))
        assertThat(billing.errorCode).isEqualTo("AI_BILLING_BLOCKED")
        assertThat(billing.retryable).isFalse()
        val auth = AiFailures.fromProvider(ModelProviderException("key", failure = ProviderFailure.AUTHENTICATION))
        assertThat(auth.errorCode).isEqualTo("AI_CONFIGURATION_ERROR")
        assertThat(auth.retryable).isFalse()
        val limited = AiFailures.fromProvider(ModelProviderException("429", failure = ProviderFailure.RATE_LIMITED))
        assertThat(limited.errorCode).isEqualTo("AI_RATE_LIMITED")
        assertThat(limited.retryable).isTrue()
        val down = AiFailures.fromProvider(ModelProviderException("reset"))
        assertThat(down.errorCode).isEqualTo("AI_PROVIDER_UNAVAILABLE")
        assertThat(down.retryable).isTrue()
    }

    @Test
    fun `guard converts gateway failures and lets others through`() {
        assertThatThrownBy {
            AiFailures.guard { throw AiBudgetExceeded(BudgetBlock.WORKSPACE_MONTHLY_BUDGET, AiUsageSnapshot(0, 0, 0)) }
        }.isInstanceOf(JobFailure::class.java)
            .hasFieldOrPropertyWithValue("errorCode", "AI_BUDGET_EXCEEDED")
        assertThatThrownBy {
            AiFailures.guard { throw IllegalStateException("bug") }
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(AiFailures.guard { 42 }).isEqualTo(42)
    }
}
