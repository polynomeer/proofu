package com.proofu.domain.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.RequirementId
import com.proofu.domain.evidence.ClaimStatus
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

    @Test
    fun `certainty is capped by the evidence level of the cited claims`() {
        val weak = ClaimId(Fixtures.uuid(53))
        val output =
            GeneratedOutput(
                listOf(
                    block("ok", Certainty.SUPPORTED),
                    block("weak", Certainty.SUPPORTED, claims = setOf(knownClaim, weak)),
                    block("none", Certainty.SUPPORTED, claims = emptySet(), evidence = emptySet()),
                    block("kept", Certainty.UNSUPPORTED),
                    block("approved", Certainty.INFERRED, approved = true),
                ),
            )

        val capped =
            output.withCertaintyCeiling { id ->
                when (id) {
                    knownClaim -> ClaimStatus.SUPPORTED
                    weak -> ClaimStatus.UNSUPPORTED
                    else -> null
                }
            }

        val byId = capped.blocks.associateBy { it.blockId }
        assertThat(byId.getValue("ok").certainty).isEqualTo(Certainty.SUPPORTED)
        assertThat(byId.getValue("ok").warnings).isEmpty()
        assertThat(byId.getValue("weak").certainty).isEqualTo(Certainty.INFERRED)
        assertThat(byId.getValue("weak").warnings).hasSize(1)
        assertThat(byId.getValue("none").certainty).isEqualTo(Certainty.UNSUPPORTED)
        assertThat(byId.getValue("kept").certainty).isEqualTo(Certainty.UNSUPPORTED)
        assertThat(byId.getValue("approved").approvedByUser).isFalse()
    }

    @Test
    fun `user revisions may approve and edit but not add references or raise certainty`() {
        val parent = GeneratedOutput(listOf(block("a", Certainty.INFERRED), block("b", Certainty.SUPPORTED)))

        val revised =
            parent.userRevision(
                listOf(
                    block("a", Certainty.SUPPORTED, approved = true).copy(text = "edited"),
                    GeneratedBlock("c", "typed by hand", certainty = Certainty.SUPPORTED),
                ),
            )

        val byId = revised.blocks.associateBy { it.blockId }
        assertThat(byId.getValue("a").certainty).isEqualTo(Certainty.INFERRED)
        assertThat(byId.getValue("a").approvedByUser).isTrue()
        assertThat(byId.getValue("a").text).isEqualTo("edited")
        assertThat(byId.getValue("c").certainty).isEqualTo(Certainty.UNSUPPORTED)
        assertThat(byId).doesNotContainKey("b")

        val foreign = ClaimId(Fixtures.uuid(77))
        assertThatThrownBy {
            parent.userRevision(
                listOf(block("b", Certainty.SUPPORTED, claims = setOf(knownClaim, foreign))),
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy {
            parent.userRevision(
                listOf(GeneratedBlock("new", "x", claimRefs = setOf(knownClaim), certainty = Certainty.SUPPORTED)),
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
    }
}
