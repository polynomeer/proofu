package com.proofu.api.evidence

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.evidence.Evidence
import com.proofu.domain.evidence.EvidenceSource
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
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
@Table(name = "evidence")
class EvidenceEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Enumerated(EnumType.STRING)
    var type: EvidenceType,
    var title: String,
    @Enumerated(EnumType.STRING)
    var source: EvidenceSource,
    @Column(columnDefinition = "text")
    var uri: String?,
    @Column(name = "object_key")
    var objectKey: String?,
    @Column(columnDefinition = "text")
    var body: String?,
    @Enumerated(EnumType.STRING)
    var verification: VerificationStatus,
    @Enumerated(EnumType.STRING)
    var sensitivity: Sensitivity,
    var confidence: BigDecimal,
    @Column(name = "captured_at")
    var capturedAt: Instant,
    var revision: Long,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun apply(e: Evidence) {
        require(e.id.value == id && e.workspaceId.value == workspaceId) { "entity identity mismatch" }
        type = e.type
        title = e.title
        source = e.source
        uri = e.uri
        objectKey = e.objectKey
        body = e.body
        verification = e.verification
        sensitivity = e.sensitivity
        confidence = BigDecimal.valueOf(e.confidence.value).setScale(2, RoundingMode.HALF_UP)
        capturedAt = e.capturedAt
        revision = e.revision.value
    }

    fun toDomain(): Evidence =
        Evidence(
            id = EvidenceId(id),
            workspaceId = WorkspaceId(workspaceId),
            type = type,
            title = title,
            source = source,
            capturedAt = capturedAt,
            uri = uri,
            objectKey = objectKey,
            body = body,
            verification = verification,
            sensitivity = sensitivity,
            confidence = Confidence(confidence.toDouble()),
            revision = Revision(revision),
        )

    companion object {
        fun from(e: Evidence): EvidenceEntity =
            EvidenceEntity(
                id = e.id.value,
                workspaceId = e.workspaceId.value,
                type = e.type,
                title = e.title,
                source = e.source,
                uri = e.uri,
                objectKey = e.objectKey,
                body = e.body,
                verification = e.verification,
                sensitivity = e.sensitivity,
                confidence = BigDecimal.valueOf(e.confidence.value).setScale(2, RoundingMode.HALF_UP),
                capturedAt = e.capturedAt,
                revision = e.revision.value,
            )
    }
}
