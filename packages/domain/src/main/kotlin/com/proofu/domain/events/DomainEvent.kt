package com.proofu.domain.events

import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.DocumentId
import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.jobs.SnapshotSource
import java.time.Instant
import java.util.UUID

/** Event contract (api/events.md). Published in-process for now and mirrored into audit_events. */
sealed interface DomainEvent {
    val eventId: UUID
    val occurredAt: Instant
}

data class CareerEntryChanged(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val workspaceId: WorkspaceId,
    val entityId: CareerEntryId,
    val revision: Revision,
) : DomainEvent

data class JobPostingSnapshotCreated(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val snapshotId: JobPostingSnapshotId,
    val source: SnapshotSource,
    val contentHash: String,
) : DomainEvent

data class DocumentVersionCreated(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val documentId: DocumentId,
    val versionId: DocumentVersionId,
    val provenanceCount: Int,
) : DomainEvent

data class ApplicationStatusChanged(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val applicationId: ApplicationId,
    val from: ApplicationStatus?,
    val to: ApplicationStatus,
) : DomainEvent

data class InterviewHandoffRequested(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val applicationId: ApplicationId,
    val snapshotRef: JobPostingSnapshotId,
    val documentRefs: List<DocumentVersionId>,
    val consentAt: Instant,
) : DomainEvent
