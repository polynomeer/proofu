package com.proofu.api.careerlog

import com.proofu.domain.career.CareerLog
import com.proofu.domain.career.CareerLogLink
import com.proofu.domain.career.CareerLogLinkType
import com.proofu.domain.career.CareerLogPromotionType
import com.proofu.domain.common.CareerLogId
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Matches `CareerLogInput` in the contract. */
data class CareerLogRequest(
    @field:NotNull val occurredAt: LocalDate?,
    @field:NotBlank
    @field:Size(max = MAX_BODY_LENGTH, message = "{max}자 이하로 입력하세요")
    val body: String?,
    /** Omitted means CONFIDENTIAL: a work journal is the employer's information by default. */
    val sensitivity: Sensitivity? = null,
    @field:Size(max = MAX_LINKS) val careerEntryIds: List<UUID> = emptyList(),
    @field:Size(max = MAX_LINKS) val projectIds: List<UUID> = emptyList(),
    val revision: Long? = null,
) {
    fun links(): List<CareerLogLink> =
        careerEntryIds.distinct().map { CareerLogLink(CareerLogLinkType.CAREER_ENTRY, it) } +
            projectIds.distinct().map { CareerLogLink(CareerLogLinkType.PROJECT, it) }

    /** A new log; [today] decides whether the date is in the future. */
    fun toNewDomain(
        id: CareerLogId,
        workspaceId: WorkspaceId,
        today: LocalDate,
    ): CareerLog =
        CareerLog.written(
            id = id,
            workspaceId = workspaceId,
            occurredAt = requireNotNull(occurredAt),
            body = requireNotNull(body).trim(),
            today = today,
            sensitivity = sensitivity ?: Sensitivity.CONFIDENTIAL,
            links = links(),
        )

    /**
     * The edited log. It goes through [CareerLog.written] too, so a log cannot be moved into the
     * future by an update either; the promotion is carried over by the caller.
     */
    fun toRevisedDomain(
        current: CareerLog,
        today: LocalDate,
    ): CareerLog =
        toNewDomain(current.id, current.workspaceId, today)
            .copy(promotion = current.promotion, revision = current.revision)
}

data class CareerLogPromotionRequest(
    @field:NotNull val targetType: CareerLogPromotionType?,
    @field:NotNull val targetId: UUID?,
)

data class CareerLogPromotionResponse(
    val promotedAt: Instant,
    /** Null once the promoted record was permanently deleted; the date stays. */
    val targetType: CareerLogPromotionType?,
    val targetId: UUID?,
)

data class CareerLogResponse(
    val id: UUID,
    val occurredAt: LocalDate,
    val body: String,
    val sensitivity: Sensitivity,
    val careerEntryIds: List<UUID>,
    val projectIds: List<UUID>,
    val promotion: CareerLogPromotionResponse?,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: CareerLogEntity,
            links: List<CareerLogLink>,
        ): CareerLogResponse =
            CareerLogResponse(
                id = e.id,
                occurredAt = e.occurredAt,
                body = e.body,
                sensitivity = e.sensitivity,
                careerEntryIds = links.filter { it.type == CareerLogLinkType.CAREER_ENTRY }.map { it.id },
                projectIds = links.filter { it.type == CareerLogLinkType.PROJECT }.map { it.id },
                promotion =
                    e.promotion()?.let { p ->
                        CareerLogPromotionResponse(p.promotedAt, p.target?.type, p.target?.id)
                    },
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class CareerLogPage(
    val items: List<CareerLogResponse>,
    val nextCursor: String?,
)

const val MAX_BODY_LENGTH = 2000
const val MAX_LINKS = 20
