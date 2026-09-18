package com.proofu.api.jobs

import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.jobs.JobPosting
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job_postings")
class JobPostingEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "external_posting_id")
    var externalPostingId: String?,
    @Column(name = "canonical_url", columnDefinition = "text")
    var canonicalUrl: String?,
    var company: String,
    @Column(name = "role_title")
    var roleTitle: String,
    @Column(name = "published_at")
    var publishedAt: Instant?,
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
        JobPosting(
            JobPostingId(id),
            WorkspaceId(workspaceId),
            company,
            roleTitle,
            canonicalUrl,
            externalPostingId,
            publishedAt,
        )

    companion object {
        fun from(p: JobPosting) =
            JobPostingEntity(
                id = p.id.value,
                workspaceId = p.workspaceId.value,
                externalPostingId = p.externalPostingId,
                canonicalUrl = p.canonicalUrl,
                company = p.company,
                roleTitle = p.roleTitle,
                publishedAt = p.publishedAt,
            )
    }
}
