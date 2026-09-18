package com.proofu.api.requirement

import com.proofu.domain.jobs.RequirementCategory
import com.proofu.domain.jobs.RequirementOrigin
import com.proofu.domain.jobs.RequirementStatus
import com.proofu.domain.jobs.SourceSpan
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SourceSpanRequest(
    @field:Min(0) val start: Int,
    @field:Min(1) val end: Int,
) {
    fun toDomain(): SourceSpan = SourceSpan(start, end)
}

data class CreateRequirementRequest(
    @field:NotNull val category: RequirementCategory?,
    @field:NotBlank @field:Size(max = 1000, message = "{max}자 이하로 입력하세요") val text: String?,
    val sourceSpan: SourceSpanRequest? = null,
)

/** Omitted fields keep their value; `sourceSpan` set to null explicitly clears the span (see [clearSpan]). */
data class UpdateRequirementRequest(
    @field:NotNull val version: Long?,
    val category: RequirementCategory? = null,
    @field:Size(max = 1000, message = "{max}자 이하로 입력하세요") val text: String? = null,
    val sourceSpan: SourceSpanRequest? = null,
    val clearSpan: Boolean = false,
    val status: RequirementStatus? = null,
)

data class RequirementResponse(
    val id: UUID,
    val snapshotId: UUID,
    val category: RequirementCategory,
    val text: String,
    val sourceSpan: SourceSpan?,
    val excerpt: String?,
    val confidence: Double,
    val origin: RequirementOrigin,
    val status: RequirementStatus,
    val approvedAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: RequirementEntity,
            excerpt: String?,
        ) = RequirementResponse(
            id = e.id,
            snapshotId = e.snapshotId,
            category = e.category,
            text = e.text,
            sourceSpan = e.sourceSpan,
            excerpt = excerpt,
            confidence = e.confidence.toDouble(),
            origin = e.origin,
            status = e.status,
            approvedAt = e.approvedAt,
            version = e.version,
            createdAt = checkNotNull(e.createdAt),
            updatedAt = checkNotNull(e.updatedAt),
        )
    }
}

data class RequirementList(
    val items: List<RequirementResponse>,
)
