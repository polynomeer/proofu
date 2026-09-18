package com.proofu.api.career

import com.proofu.domain.career.CareerEntry
import com.proofu.domain.career.CareerEntryStatus
import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Matches `CareerEntryInput` in packages/contracts/openapi.yaml. Fields are nullable so bean validation reports them. */
data class CareerEntryRequest(
    @field:NotNull val type: CareerEntryType?,
    @field:NotBlank @field:Size(max = 200) val title: String?,
    @field:Size(max = 200) val organization: String? = null,
    @field:Size(max = 200) val location: String? = null,
    val description: String? = null,
    @field:NotNull val startDate: LocalDate?,
    val endDate: LocalDate? = null,
    val visibility: Visibility? = null,
    /** Required on PATCH; the stored revision must match (optimistic locking). */
    val revision: Long? = null,
) {
    fun toDomain(
        id: CareerEntryId,
        workspaceId: WorkspaceId,
        status: CareerEntryStatus,
        revision: Revision,
    ): CareerEntry =
        CareerEntry(
            id = id,
            workspaceId = workspaceId,
            type = requireNotNull(type),
            title = requireNotNull(title).trim(),
            startDate = requireNotNull(startDate),
            endDate = endDate,
            organization = organization?.trim()?.ifEmpty { null },
            location = location?.trim()?.ifEmpty { null },
            description = description?.trim()?.ifEmpty { null },
            visibility = visibility ?: Visibility.PRIVATE,
            status = status,
            revision = revision,
        )
}

data class CareerEntryResponse(
    val id: UUID,
    val type: CareerEntryType,
    val title: String,
    val organization: String?,
    val location: String?,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val visibility: Visibility,
    val status: CareerEntryStatus,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: CareerEntryEntity) =
            CareerEntryResponse(
                id = e.id,
                type = e.type,
                title = e.title,
                organization = e.organization,
                location = e.location,
                description = e.description,
                startDate = e.startDate,
                endDate = e.endDate,
                visibility = e.visibility,
                status = e.status,
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class CareerEntryPage(
    val items: List<CareerEntryResponse>,
    val nextCursor: String?,
)
