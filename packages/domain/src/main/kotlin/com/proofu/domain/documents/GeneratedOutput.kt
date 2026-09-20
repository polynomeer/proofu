package com.proofu.domain.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.evidence.ClaimStatus

/** Grounding level of a generated block (AI §9.3). */
enum class Certainty {
    SUPPORTED,
    INFERRED,
    UNSUPPORTED,
    ;

    /** Only SUPPORTED blocks may be exported without explicit user approval. */
    val exportableWithoutApproval: Boolean get() = this == SUPPORTED

    /** Weaker of two levels; the enum is declared strongest first. */
    fun atMost(other: Certainty): Certainty = if (other.ordinal > ordinal) other else this

    companion object {
        /**
         * The strongest certainty a block may claim given the claims it cites (grounding policy):
         * no claim → UNSUPPORTED, any cited claim without supporting evidence → INFERRED,
         * every cited claim SUPPORTED → SUPPORTED. The model's own label can only lower this.
         */
        fun ceiling(citedClaimStatuses: Collection<ClaimStatus>): Certainty =
            when {
                citedClaimStatuses.isEmpty() -> UNSUPPORTED
                citedClaimStatuses.all { it == ClaimStatus.SUPPORTED } -> SUPPORTED
                else -> INFERRED
            }
    }
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

    /**
     * Caps every block's certainty at [Certainty.ceiling] of the claims it cites. Unknown claims
     * count as UNSUPPORTED, so run [requireGrounded] first. Blocks the model labelled weaker keep
     * their label; an AI draft is never approved.
     */
    fun withCertaintyCeiling(claimStatus: (ClaimId) -> ClaimStatus?): GeneratedOutput =
        GeneratedOutput(
            blocks.map { block ->
                val statuses = block.claimRefs.map { claimStatus(it) ?: ClaimStatus.UNSUPPORTED }
                val ceiling = Certainty.ceiling(statuses)
                val warning = "인용한 주장의 근거 수준(${ceiling.name})을 넘는 확신도(${block.certainty.name})를 낮췄습니다."
                if (block.certainty.ordinal < ceiling.ordinal) {
                    block.copy(certainty = ceiling, warnings = block.warnings + warning, approvedByUser = false)
                } else {
                    block.copy(approvedByUser = false)
                }
            },
        )

    /**
     * A user-saved revision of this output. Users may edit text, drop blocks, reorder, and
     * approve INFERRED/UNSUPPORTED blocks, but may not invent references (every ref must already
     * appear on the same block of the parent) nor raise a block's certainty. New blocks written by
     * the user are UNSUPPORTED until they cite something (they cannot, yet), so they must be approved.
     */
    fun userRevision(edited: List<GeneratedBlock>): GeneratedOutput {
        val parents = blocks.associateBy { it.blockId }
        val checked =
            edited.map { block ->
                val parent = parents[block.blockId]
                if (parent == null) {
                    if (block.claimRefs.isNotEmpty() ||
                        block.evidenceRefs.isNotEmpty() ||
                        block.requirementRefs.isNotEmpty()
                    ) {
                        throw DomainRuleViolation("new block ${block.blockId} cannot cite sources")
                    }
                    block.copy(certainty = Certainty.UNSUPPORTED)
                } else {
                    val added =
                        (block.claimRefs - parent.claimRefs).map { "claim:${it.value}" } +
                            (block.evidenceRefs - parent.evidenceRefs).map { "evidence:${it.value}" } +
                            (block.requirementRefs - parent.requirementRefs).map { "requirement:${it.value}" }
                    if (added.isNotEmpty()) {
                        throw DomainRuleViolation("block ${block.blockId} cites sources the parent did not: $added")
                    }
                    block.copy(certainty = block.certainty.atMost(parent.certainty))
                }
            }
        return GeneratedOutput(checked)
    }
}
