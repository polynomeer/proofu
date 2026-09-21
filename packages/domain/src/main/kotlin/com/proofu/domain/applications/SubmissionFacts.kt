package com.proofu.domain.applications

import com.proofu.domain.common.RequirementId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.GeneratedOutput

/**
 * Observable facts about a submitted document version, offered as review material (A02
 * "비교 대상"). They describe the document; they never say why an application was rejected.
 */
data class SubmissionFacts(
    val totalBlocks: Int,
    val blocksWithClaims: Int,
    val supportedBlocks: Int,
    /** INFERRED/UNSUPPORTED blocks the user chose to export anyway. */
    val approvedWithoutEvidence: Int,
    /** Approved requirements at least one block addresses. */
    val addressedRequirements: Set<RequirementId>,
    /** Approved requirements no block addresses. */
    val unaddressedRequirements: Set<RequirementId>,
) {
    /** Share (0–100) of blocks that cite at least one claim. */
    val evidenceLinkRate: Int get() = percent(blocksWithClaims, totalBlocks)

    /** Share (0–100) of approved requirements some block addresses. */
    val requirementCoverage: Int get() =
        percent(
            addressedRequirements.size,
            addressedRequirements.size + unaddressedRequirements.size,
        )

    companion object {
        fun of(
            output: GeneratedOutput,
            approvedRequirements: Set<RequirementId>,
        ): SubmissionFacts {
            val addressed = output.blocks.flatMap { it.requirementRefs }.toSet() intersect approvedRequirements
            return SubmissionFacts(
                totalBlocks = output.blocks.size,
                blocksWithClaims = output.blocks.count { it.claimRefs.isNotEmpty() },
                supportedBlocks = output.blocks.count { it.certainty == Certainty.SUPPORTED },
                approvedWithoutEvidence =
                    output.blocks.count {
                        it.certainty != Certainty.SUPPORTED && it.approvedByUser
                    },
                addressedRequirements = addressed,
                unaddressedRequirements = approvedRequirements - addressed,
            )
        }

        private fun percent(
            part: Int,
            whole: Int,
        ): Int = if (whole == 0) 0 else Math.round(part * 100.0 / whole).toInt()
    }
}
