package com.proofu.api.claim

import com.proofu.domain.common.Sensitivity
import com.proofu.domain.evidence.ClaimSourceType
import com.proofu.domain.evidence.ClaimStatus
import com.proofu.domain.evidence.ClaimType
import com.proofu.domain.evidence.EvidenceRelation
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ClaimSourceRequest(
    @field:NotNull val type: ClaimSourceType?,
    @field:NotNull val id: UUID?,
)

data class ClaimRequest(
    @field:NotBlank val text: String?,
    @field:NotNull val type: ClaimType?,
    val sensitivity: Sensitivity? = null,
    @field:Valid @field:Size(max = 10) val sources: List<ClaimSourceRequest> = emptyList(),
    val revision: Long? = null,
)

data class LinkEvidenceRequest(
    @field:NotNull val evidenceId: UUID?,
    @field:NotNull val relation: EvidenceRelation?,
    @field:NotNull
    @field:DecimalMin("0.0", message = "0과 1 사이여야 합니다")
    @field:DecimalMax("1.0", message = "0과 1 사이여야 합니다")
    val confidence: Double?,
    val scope: String? = null,
)

data class ClaimSourceResponse(
    val type: ClaimSourceType,
    val id: UUID,
    val revision: Long,
)

data class ClaimEvidenceLinkResponse(
    val evidenceId: UUID,
    val evidenceTitle: String,
    val evidenceType: EvidenceType,
    val evidenceVerification: VerificationStatus,
    val relation: EvidenceRelation,
    val confidence: Double,
    val scope: String?,
)

data class ClaimResponse(
    val id: UUID,
    val text: String,
    val type: ClaimType,
    val sensitivity: Sensitivity,
    val status: ClaimStatus,
    val sources: List<ClaimSourceResponse>,
    val links: List<ClaimEvidenceLinkResponse>,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ClaimList(
    val items: List<ClaimResponse>,
)
