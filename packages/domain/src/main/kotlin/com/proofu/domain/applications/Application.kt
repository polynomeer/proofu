package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.Instant

/** Append-only record of a status change. Past states are never overwritten. */
data class ApplicationStatusEvent(
    val applicationId: ApplicationId,
    val from: ApplicationStatus?,
    val to: ApplicationStatus,
    val occurredAt: Instant,
)

data class Application(
    val id: ApplicationId,
    val workspaceId: WorkspaceId,
    val snapshotId: JobPostingSnapshotId,
    val company: String,
    val roleTitle: String,
    val status: ApplicationStatus = ApplicationStatus.INTERESTED,
    val version: Long = 1,
) {
    init {
        domainRequire(company.isNotBlank()) { "application company must not be blank" }
        domainRequire(roleTitle.isNotBlank()) { "application role title must not be blank" }
    }

    /** Returns the updated application together with the event to persist. */
    fun transition(
        to: ApplicationStatus,
        at: Instant,
    ): Pair<Application, ApplicationStatusEvent> {
        val next = status.transitionTo(to)
        return copy(status = next, version = version + 1) to ApplicationStatusEvent(id, status, next, at)
    }
}
