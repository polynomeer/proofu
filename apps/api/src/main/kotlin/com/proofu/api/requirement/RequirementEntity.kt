package com.proofu.api.requirement

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.jobs.Requirement
import com.proofu.domain.jobs.RequirementCategory
import com.proofu.domain.jobs.RequirementOrigin
import com.proofu.domain.jobs.RequirementStatus
import com.proofu.domain.jobs.SourceSpan
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "requirements")
class RequirementEntity(
    @Id
    val id: UUID,
    @Column(name = "snapshot_id")
    val snapshotId: UUID,
    @Enumerated(EnumType.STRING)
    var category: RequirementCategory,
    @Column(columnDefinition = "text")
    var text: String,
    @Column(name = "span_start")
    var spanStart: Int?,
    @Column(name = "span_end")
    var spanEnd: Int?,
    var confidence: BigDecimal,
    @Enumerated(EnumType.STRING)
    var origin: RequirementOrigin,
    @Enumerated(EnumType.STRING)
    var status: RequirementStatus,
    @Column(name = "approved_at")
    var approvedAt: Instant?,
    @Column(name = "sort_order")
    var sortOrder: Int,
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

    val sourceSpan: SourceSpan? get() = spanStart?.let { s -> spanEnd?.let { e -> SourceSpan(s, e) } }

    fun toDomain() =
        Requirement(
            id = RequirementId(id),
            snapshotId = JobPostingSnapshotId(snapshotId),
            category = category,
            text = text,
            confidence = Confidence(confidence.toDouble()),
            origin = origin,
            sourceSpan = sourceSpan,
            status = status,
            approvedAt = approvedAt,
        )

    fun apply(r: Requirement) {
        require(r.id.value == id && r.snapshotId.value == snapshotId) { "entity identity mismatch" }
        category = r.category
        text = r.text
        spanStart = r.sourceSpan?.start
        spanEnd = r.sourceSpan?.end
        confidence = BigDecimal.valueOf(r.confidence.value).setScale(2, RoundingMode.HALF_UP)
        origin = r.origin
        status = r.status
        approvedAt = r.approvedAt
    }

    companion object {
        fun from(
            r: Requirement,
            sortOrder: Int,
        ) = RequirementEntity(
            id = r.id.value,
            snapshotId = r.snapshotId.value,
            category = r.category,
            text = r.text,
            spanStart = r.sourceSpan?.start,
            spanEnd = r.sourceSpan?.end,
            confidence = BigDecimal.valueOf(r.confidence.value).setScale(2, RoundingMode.HALF_UP),
            origin = r.origin,
            status = r.status,
            approvedAt = r.approvedAt,
            sortOrder = sortOrder,
            version = 1,
        )
    }
}
