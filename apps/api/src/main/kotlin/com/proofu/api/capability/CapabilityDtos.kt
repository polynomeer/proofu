package com.proofu.api.capability

import com.proofu.domain.career.Capability
import com.proofu.domain.career.CapabilityCategory
import com.proofu.domain.career.CapabilityEvidenceStatus
import com.proofu.domain.career.ProficiencyLevel
import com.proofu.domain.common.CapabilityId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.evidence.VerificationStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Matches `CapabilityInput` in the contract. */
data class CapabilityRequest(
    @field:NotBlank
    @field:Size(max = Capability.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
    val name: String?,
    @field:NotBlank val definition: String?,
    @field:NotNull val category: CapabilityCategory?,
    val selfAssessedLevel: ProficiencyLevel? = null,
    val evidenceCriteria: String? = null,
    val parentId: UUID? = null,
    val revision: Long? = null,
) {
    fun toDomain(
        id: CapabilityId,
        workspaceId: WorkspaceId,
        revision: Revision,
    ): Capability =
        Capability(
            id = id,
            workspaceId = workspaceId,
            name = requireNotNull(name).trim(),
            definition = requireNotNull(definition).trim(),
            category = requireNotNull(category),
            selfAssessedLevel = selfAssessedLevel,
            evidenceCriteria = evidenceCriteria?.trim()?.ifEmpty { null },
            parentId = parentId?.let(::CapabilityId),
            revision = revision,
        )
}

data class CapabilityEvidenceResponse(
    val id: UUID,
    val title: String,
    val verification: VerificationStatus,
)

data class CapabilityResponse(
    val id: UUID,
    val name: String,
    val definition: String,
    val category: CapabilityCategory,
    val selfAssessedLevel: ProficiencyLevel?,
    val evidenceCriteria: String?,
    val parentId: UUID?,
    val evidence: List<CapabilityEvidenceResponse>,
    val evidenceStatus: CapabilityEvidenceStatus,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: CapabilityEntity,
            evidence: List<CapabilityEvidenceResponse>,
        ) = CapabilityResponse(
            id = e.id,
            name = e.name,
            definition = e.definition,
            category = e.category,
            selfAssessedLevel = e.selfAssessedLevel,
            evidenceCriteria = e.evidenceCriteria,
            parentId = e.parentId,
            evidence = evidence,
            evidenceStatus = CapabilityEvidenceStatus.of(evidence.map { it.verification }),
            revision = e.revision,
            createdAt = checkNotNull(e.createdAt),
            updatedAt = checkNotNull(e.updatedAt),
        )
    }
}

data class CapabilityPage(
    val items: List<CapabilityResponse>,
    val nextCursor: String?,
)

data class CapabilityEvidenceRequest(
    @field:NotNull @field:Size(max = MAX_CAPABILITY_EVIDENCE) val evidenceIds: List<UUID>?,
)

const val MAX_CAPABILITY_EVIDENCE = 50
