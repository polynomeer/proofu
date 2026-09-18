package com.proofu.ai.policy

import com.proofu.ai.model.ContextDocument
import com.proofu.domain.common.Sensitivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextPolicyTest {
    private fun doc(
        id: String,
        sensitivity: Sensitivity,
        length: Int = 30,
    ) = ContextDocument(id, "t", "x".repeat(length), sensitivity)

    @Test
    fun `confidential and restricted documents are excluded unless consented`() {
        val policy = ContextPolicy(maxInputTokens = 10_000)
        val docs =
            listOf(
                doc("a", Sensitivity.PUBLIC),
                doc("b", Sensitivity.CONFIDENTIAL),
                doc("c", Sensitivity.RESTRICTED),
                doc("d", Sensitivity.INTERNAL),
            )

        val without = policy.apply(docs, consentToSensitive = false, reservedTokens = 0)
        assertThat(without.includedSourceIds).containsExactly("a", "d")
        assertThat(
            without.excluded,
        ).containsEntry("b", ExclusionReason.SENSITIVITY).containsEntry("c", ExclusionReason.SENSITIVITY)

        val with = policy.apply(docs, consentToSensitive = true, reservedTokens = 0)
        assertThat(with.includedSourceIds).containsExactly("a", "b", "c", "d")
    }

    @Test
    fun `documents past the token budget are dropped in caller order`() {
        val policy = ContextPolicy(maxInputTokens = 100)
        val docs =
            listOf(
                doc("first", Sensitivity.PUBLIC, 60),
                doc("second", Sensitivity.PUBLIC, 60),
                doc("third", Sensitivity.PUBLIC, 10),
            )

        val decision = policy.apply(docs, consentToSensitive = false, reservedTokens = 20)

        assertThat(decision.includedSourceIds).containsExactly("first", "third")
        assertThat(decision.excluded).containsEntry("second", ExclusionReason.TOKEN_BUDGET)
    }
}
