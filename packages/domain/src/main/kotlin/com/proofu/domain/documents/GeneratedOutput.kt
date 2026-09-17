package com.proofu.domain.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.RequirementId

/** Grounding level of a generated block (AI §9.3). */
enum class Certainty {
    SUPPORTED,
    INFERRED,
    UNSUPPORTED,
    ;

    /** Only SUPPORTED blocks may be exported without explicit user approval. */
    val exportableWithoutApproval: Boolean get() = this == SUPPORTED
}

data class GeneratedBlock(
    val blockId: String,
    val text: String,
    val claimRefs: Set<ClaimId> = emptySet(),
    val evidenceRefs: Set<EvidenceId> = emptySet(),
    val requirementRefs: Set<RequirementId> = emptySet(),
    val certainty: Certainty,
    val warnings: List<String> = emptyList(),
    val approvedByUser: Boolean = false,
) {
    init {
        require(blockId.isNotBlank()) { "block id must not be blank" }
    }

    val exportable: Boolean get() = certainty.exportableWithoutApproval || approvedByUser
}

/** The set of sources the model was allowed to see. Anything else in the output is a hallucinated reference. */
data class AllowedSources(
    val claims: Set<ClaimId>,
    val evidence: Set<EvidenceId>,
    val requirements: Set<RequirementId>,
)

data class GeneratedOutput(
    val blocks: List<GeneratedBlock>,
) {
    init {
        val duplicates =
            blocks
                .groupingBy { it.blockId }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicates.isEmpty()) { "duplicate block ids: $duplicates" }
    }

    /**
     * Server-side whitelist validation (grounding policy step 6). Returns the blocks that
     * reference identifiers outside [allowed]; an empty result means the output is accepted.
     */
    fun unknownReferences(allowed: AllowedSources): Map<String, Set<String>> =
        blocks
            .associate { block ->
                val unknown =
                    (block.claimRefs - allowed.claims).map { "claim:${it.value}" } +
                        (block.evidenceRefs - allowed.evidence).map { "evidence:${it.value}" } +
                        (block.requirementRefs - allowed.requirements).map { "requirement:${it.value}" }
                block.blockId to unknown.toSet()
            }.filterValues { it.isNotEmpty() }

    fun requireGrounded(allowed: AllowedSources): GeneratedOutput {
        val unknown = unknownReferences(allowed)
        if (unknown.isNotEmpty()) {
            throw DomainRuleViolation("generated output references unknown sources: $unknown")
        }
        return this
    }

    /** Blocks that block an export until the user approves them. */
    fun blocksPendingApproval(): List<GeneratedBlock> = blocks.filterNot { it.exportable }
}
