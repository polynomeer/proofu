package com.proofu.api.application

import com.proofu.domain.applications.Application
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.WorkspaceId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "applications")
class ApplicationEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "snapshot_id")
    val snapshotId: UUID,
    var company: String,
    @Column(name = "role_title")
    var roleTitle: String,
    @Enumerated(EnumType.STRING)
    var status: ApplicationStatus,
    @Column(name = "deadline_at")
    var deadlineAt: Instant?,
    var version: Long,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun toDomain() =
        Application(
            ApplicationId(id),
            WorkspaceId(workspaceId),
            JobPostingSnapshotId(snapshotId),
            company,
            roleTitle,
            status,
            version,
        )

    fun apply(a: Application) {
        require(a.id.value == id && a.workspaceId.value == workspaceId) { "entity identity mismatch" }
        status = a.status
        version = a.version
    }

    companion object {
        fun from(
            a: Application,
            deadlineAt: Instant?,
        ) = ApplicationEntity(
            id = a.id.value,
            workspaceId = a.workspaceId.value,
            snapshotId = a.snapshotId.value,
            company = a.company,
            roleTitle = a.roleTitle,
            status = a.status,
            deadlineAt = deadlineAt,
            version = a.version,
        )
    }
}
