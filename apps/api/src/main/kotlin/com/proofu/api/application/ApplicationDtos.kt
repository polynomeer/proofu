package com.proofu.api.application

import com.proofu.api.jobs.SnapshotSummary
import com.proofu.domain.applications.ApplicationStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateApplicationRequest(
    @field:NotNull val snapshotId: UUID?,
    val deadlineAt: Instant? = null,
)

data class UpdateApplicationRequest(
    val deadlineAt: Instant? = null,
    @field:NotNull val version: Long?,
)

data class TransitionRequest(
    @field:NotNull val to: ApplicationStatus?,
    @field:Size(max = 500, message = "{max}자 이하로 입력하세요") val note: String? = null,
    @field:NotNull val version: Long?,
)

data class ApplicationStatusEventResponse(
    val from: ApplicationStatus?,
    val to: ApplicationStatus,
    val note: String?,
    val occurredAt: Instant,
)

data class ApplicationResponse(
    val id: UUID,
    val snapshotId: UUID,
    val postingId: UUID,
    val company: String,
    val roleTitle: String,
    val status: ApplicationStatus,
    val allowedTransitions: List<ApplicationStatus>,
    val statusChangedAt: Instant,
    val deadlineAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /** HANDED_OFF_TO_ITERVIEW is only reachable through an interview handoff, never by a plain transition. */
        fun allowedFrom(status: ApplicationStatus): List<ApplicationStatus> =
            ApplicationStatus.entries.filter {
                status.canTransitionTo(it) &&
                    it != ApplicationStatus.HANDED_OFF_TO_ITERVIEW
            }

        fun from(
            e: ApplicationEntity,
            postingId: UUID,
            statusChangedAt: Instant,
        ) = ApplicationResponse(
            id = e.id,
            snapshotId = e.snapshotId,
            postingId = postingId,
            company = e.company,
            roleTitle = e.roleTitle,
            status = e.status,
            allowedTransitions = allowedFrom(e.status),
            statusChangedAt = statusChangedAt,
            deadlineAt = e.deadlineAt,
            version = e.version,
            createdAt = checkNotNull(e.createdAt),
            updatedAt = checkNotNull(e.updatedAt),
        )
    }
}

data class ApplicationDetail(
    val id: UUID,
    val snapshotId: UUID,
    val postingId: UUID,
    val company: String,
    val roleTitle: String,
    val status: ApplicationStatus,
    val allowedTransitions: List<ApplicationStatus>,
    val statusChangedAt: Instant,
    val deadlineAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val events: List<ApplicationStatusEventResponse>,
    val snapshot: SnapshotSummary,
) {
    companion object {
        fun from(
            base: ApplicationResponse,
            events: List<ApplicationStatusEventResponse>,
            snapshot: SnapshotSummary,
        ) = ApplicationDetail(
            base.id,
            base.snapshotId,
            base.postingId,
            base.company,
            base.roleTitle,
            base.status,
            base.allowedTransitions,
            base.statusChangedAt,
            base.deadlineAt,
            base.version,
            base.createdAt,
            base.updatedAt,
            events,
            snapshot,
        )
    }
}

data class ApplicationList(
    val items: List<ApplicationResponse>,
    /** The board shows the most recently changed applications; older ones exist beyond this. */
    val truncated: Boolean,
)
