package com.proofu.domain.evidence

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.WorkspaceBoundaryViolation
import com.proofu.domain.common.domainRequire

enum class EvidenceRelation {
    SUPPORTS,
    REFUTES,
    PARTIALLY_SUPPORTS,
}

data class ClaimEvidenceLink(
    val claimId: ClaimId,
    val evidenceId: EvidenceId,
    val relation: EvidenceRelation,
    val confidence: Confidence,
    /** Required for PARTIALLY_SUPPORTS: which part of the claim the evidence covers. */
    val scope: String? = null,
) {
    init {
        domainRequire(relation != EvidenceRelation.PARTIALLY_SUPPORTS || !scope.isNullOrBlank()) {
            "partial support requires a scope describing what is covered"
        }
    }

    companion object {
        /** Links may only be created inside one workspace. */
        fun link(
            claim: Claim,
            evidence: Evidence,
            relation: EvidenceRelation,
            confidence: Confidence,
            scope: String? = null,
        ): ClaimEvidenceLink {
            if (claim.workspaceId != evidence.workspaceId) {
                throw WorkspaceBoundaryViolation(
                    "evidence ${evidence.id.value} belongs to another workspace than claim ${claim.id.value}",
                )
            }
            return ClaimEvidenceLink(claim.id, evidence.id, relation, confidence, scope)
        }

        /** Derives the claim status from its links. Refutations make a claim CONTESTED. */
        fun statusOf(links: Collection<ClaimEvidenceLink>): ClaimStatus =
            when {
                links.isEmpty() -> ClaimStatus.UNSUPPORTED
                links.any { it.relation == EvidenceRelation.REFUTES } -> ClaimStatus.CONTESTED
                else -> ClaimStatus.SUPPORTED
            }
    }
}
