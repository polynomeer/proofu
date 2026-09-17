package com.proofu.domain.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.RequirementId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GeneratedOutputTest {
    private val knownClaim = ClaimId(Fixtures.uuid(50))
    private val knownEvidence = EvidenceId(Fixtures.uuid(51))
    private val knownRequirement = RequirementId(Fixtures.uuid(52))
    private val allowed = AllowedSources(setOf(knownClaim), setOf(knownEvidence), setOf(knownRequirement))

    private fun block(
        id: String,
        certainty: Certainty,
        claims: Set<ClaimId> = setOf(knownClaim),
        evidence: Set<EvidenceId> = setOf(knownEvidence),
        approved: Boolean = false,
    ) = GeneratedBlock(
        blockId = id,
        text = "Reduced latency by 40%",
        claimRefs = claims,
        evidenceRefs = evidence,
        requirementRefs = setOf(knownRequirement),
        certainty = certainty,
        approvedByUser = approved,
    )

    @Test
    fun `references outside the allowed set are reported per block`() {
        val hallucinated = ClaimId(Fixtures.uuid(99))
        val output =
            GeneratedOutput(
                listOf(
                    block("b1", Certainty.SUPPORTED),
                    block("b2", Certainty.SUPPORTED, claims = setOf(knownClaim, hallucinated)),
                ),
            )

        assertThat(output.unknownReferences(allowed))
            .containsOnlyKeys("b2")
            .containsEntry("b2", setOf("claim:${hallucinated.value}"))
        assertThatThrownBy { output.requireGrounded(allowed) }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `grounded output passes validation`() {
        val output = GeneratedOutput(listOf(block("b1", Certainty.SUPPORTED)))
        assertThat(output.requireGrounded(allowed)).isSameAs(output)
    }

    @Test
    fun `inferred and unsupported blocks need user approval before export`() {
        val output =
            GeneratedOutput(
                listOf(
                    block("s", Certainty.SUPPORTED),
                    block("i", Certainty.INFERRED),
                    block("u", Certainty.UNSUPPORTED, evidence = emptySet()),
                    block("ua", Certainty.UNSUPPORTED, evidence = emptySet(), approved = true),
                ),
            )

        assertThat(output.blocksPendingApproval().map { it.blockId }).containsExactly("i", "u")
    }

    @Test
    fun `block ids must be unique`() {
        assertThatThrownBy {
            GeneratedOutput(listOf(block("dup", Certainty.SUPPORTED), block("dup", Certainty.SUPPORTED)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
