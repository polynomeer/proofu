package com.proofu.api.achievement

import com.proofu.domain.career.Achievement
import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Matches `AchievementInput` in the contract. */
data class AchievementRequest(
    @field:NotBlank val action: String?,
    @field:NotBlank val outcome: String?,
    val metricValue: BigDecimal? = null,
    @field:Size(max = 40, message = "{max}자 이하로 입력하세요") val metricUnit: String? = null,
    @field:Size(max = 200, message = "{max}자 이하로 입력하세요") val baseline: String? = null,
    @field:Size(max = 120, message = "{max}자 이하로 입력하세요") val timeframe: String? = null,
    @field:NotNull
    @field:DecimalMin("0.0", message = "0과 1 사이여야 합니다")
    @field:DecimalMax("1.0", message = "0과 1 사이여야 합니다")
    val confidence: Double?,
    val revision: Long? = null,
) {
    fun toDomain(
        id: AchievementId,
        workspaceId: WorkspaceId,
        projectId: ProjectId,
        revision: Revision,
    ): Achievement =
        Achievement(
            id = id,
            workspaceId = workspaceId,
            projectId = projectId,
            action = requireNotNull(action).trim(),
            outcome = requireNotNull(outcome).trim(),
            confidence = Confidence(requireNotNull(confidence)),
            metricValue = metricValue,
            metricUnit = metricUnit?.trim()?.ifEmpty { null },
            baseline = baseline?.trim()?.ifEmpty { null },
            timeframe = timeframe?.trim()?.ifEmpty { null },
            revision = revision,
        )
}

data class AchievementResponse(
    val id: UUID,
    val projectId: UUID,
    val action: String,
    val outcome: String,
    val metricValue: BigDecimal?,
    val metricUnit: String?,
    val baseline: String?,
    val timeframe: String?,
    val confidence: Double,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: AchievementEntity) =
            AchievementResponse(
                id = e.id,
                projectId = e.projectId,
                action = e.action,
                outcome = e.outcome,
                metricValue = e.metricValue?.let(::plain),
                metricUnit = e.metricUnit,
                baseline = e.baseline,
                timeframe = e.timeframe,
                confidence = e.confidence.toDouble(),
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

/** 40.0000 → 40, 12.50 → 12.5, never exponent notation (4E+1) in JSON. */
private fun plain(value: BigDecimal): BigDecimal =
    value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

data class AchievementList(
    val items: List<AchievementResponse>,
)
