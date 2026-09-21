package com.proofu.ai.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProviderFailureTest {
    @Test
    fun `billing is told apart from other 400s by the provider message`() {
        val billing =
            ProviderFailure.classify(
                400,
                """400: {"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits."}}""",
            )
        assertThat(billing).isEqualTo(ProviderFailure.BILLING)
        assertThat(billing.retryable).isFalse()
        assertThat(billing.operatorAction).isTrue()
        assertThat(
            ProviderFailure.classify(400, "output_config.format schema is invalid"),
        ).isEqualTo(ProviderFailure.INVALID_REQUEST)
    }

    @Test
    fun `auth, rate limit, overload and transport map to their kinds`() {
        assertThat(ProviderFailure.classify(401, "invalid x-api-key")).isEqualTo(ProviderFailure.AUTHENTICATION)
        assertThat(ProviderFailure.classify(403, "permission")).isEqualTo(ProviderFailure.AUTHENTICATION)
        assertThat(ProviderFailure.classify(429, "rate limit")).isEqualTo(ProviderFailure.RATE_LIMITED)
        assertThat(ProviderFailure.classify(529, "overloaded")).isEqualTo(ProviderFailure.OVERLOADED)
        assertThat(ProviderFailure.classify(500, "internal")).isEqualTo(ProviderFailure.UNAVAILABLE)
        assertThat(ProviderFailure.classify(null, "connection reset")).isEqualTo(ProviderFailure.UNAVAILABLE)
        assertThat(ProviderFailure.entries.filter { it.retryable }).containsExactlyInAnyOrder(
            ProviderFailure.RATE_LIMITED,
            ProviderFailure.OVERLOADED,
            ProviderFailure.UNAVAILABLE,
        )
    }
}
