package com.proofu.domain.applications

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.RequirementId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SubmissionFactsTest {
    private val r1 = RequirementId(Fixtures.uuid(1))
    private val r2 = RequirementId(Fixtures.uuid(2))
    private val r3 = RequirementId(Fixtures.uuid(3))
    private val rejected = RequirementId(Fixtures.uuid(4))
    private val claim = ClaimId(Fixtures.uuid(9))

    @Test
    fun `counts evidence links and requirement coverage against approved requirements only`() {
        val output =
            GeneratedOutput(
                listOf(
                    GeneratedBlock(
                        "a-1",
                        "x",
                        claimRefs = setOf(claim),
                        requirementRefs = setOf(r1),
                        certainty = Certainty.SUPPORTED,
                    ),
                    GeneratedBlock(
                        "a-2",
                        "y",
                        claimRefs = setOf(claim),
                        requirementRefs = setOf(r2, rejected),
                        certainty = Certainty.INFERRED,
                        approvedByUser = true,
                    ),
                    GeneratedBlock("b-1", "z", certainty = Certainty.UNSUPPORTED, approvedByUser = true),
                ),
            )

        val facts = SubmissionFacts.of(output, setOf(r1, r2, r3))

        assertThat(facts.totalBlocks).isEqualTo(3)
        assertThat(facts.blocksWithClaims).isEqualTo(2)
        assertThat(facts.supportedBlocks).isEqualTo(1)
        assertThat(facts.approvedWithoutEvidence).isEqualTo(2)
        assertThat(facts.evidenceLinkRate).isEqualTo(67)
        assertThat(facts.addressedRequirements).containsExactlyInAnyOrder(r1, r2)
        assertThat(facts.unaddressedRequirements).containsExactly(r3)
        assertThat(facts.requirementCoverage).isEqualTo(67)
    }

    @Test
    fun `empty inputs yield zero rates rather than division errors`() {
        val facts = SubmissionFacts.of(GeneratedOutput(emptyList()), emptySet())
        assertThat(facts.evidenceLinkRate).isZero()
        assertThat(facts.requirementCoverage).isZero()
    }
}
