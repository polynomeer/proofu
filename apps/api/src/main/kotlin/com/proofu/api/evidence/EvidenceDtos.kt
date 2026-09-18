package com.proofu.api.evidence

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.evidence.Evidence
import com.proofu.domain.evidence.EvidenceSource
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Matches `EvidenceInput` in the contract. */
data class EvidenceRequest(
    @field:NotNull val type: EvidenceType?,
    @field:NotBlank @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val title: String?,
    @field:Size(max = 2048, message = "{max}자 이하로 입력하세요") val uri: String? = null,
    val body: String? = null,
    val capturedAt: Instant? = null,
    val verification: VerificationStatus? = null,
    val sensitivity: Sensitivity? = null,
    val revision: Long? = null,
) {
    fun toDomain(
        id: EvidenceId,
        workspaceId: WorkspaceId,
        source: EvidenceSource,
        capturedAtDefault: Instant,
        verification: VerificationStatus,
        confidence: Confidence,
        revision: Revision,
    ): Evidence =
        Evidence(
            id = id,
            workspaceId = workspaceId,
            type = requireNotNull(type),
            title = requireNotNull(title).trim(),
            source = source,
            capturedAt = capturedAt ?: capturedAtDefault,
            uri = uri?.trim()?.ifEmpty { null },
            objectKey = null,
            body = body?.trim()?.ifEmpty { null },
            verification = verification,
            sensitivity = sensitivity ?: Sensitivity.INTERNAL,
            confidence = confidence,
            revision = revision,
        )
}

data class EvidenceResponse(
    val id: UUID,
    val type: EvidenceType,
    val title: String,
    val source: EvidenceSource,
    val uri: String?,
    val body: String?,
    val capturedAt: Instant,
    val verification: VerificationStatus,
    val sensitivity: Sensitivity,
    val linkedClaimCount: Int,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: EvidenceEntity,
            linkedClaimCount: Int,
        ) = EvidenceResponse(
            id = e.id,
            type = e.type,
            title = e.title,
            source = e.source,
            uri = e.uri,
            body = e.body,
            capturedAt = e.capturedAt,
            verification = e.verification,
            sensitivity = e.sensitivity,
            linkedClaimCount = linkedClaimCount,
            revision = e.revision,
            createdAt = checkNotNull(e.createdAt),
            updatedAt = checkNotNull(e.updatedAt),
        )
    }
}

data class EvidencePage(
    val items: List<EvidenceResponse>,
    val nextCursor: String?,
)
