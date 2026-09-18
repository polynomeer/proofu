package com.proofu.api.claim

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.evidence.Claim
import com.proofu.domain.evidence.ClaimSource
import com.proofu.domain.evidence.ClaimType
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

/** claims row. Sources and evidence links live in their own tables and are loaded by [ClaimAssembler]. */
@Entity
@Table(name = "claims")
class ClaimEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(columnDefinition = "text")
    var text: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type")
    var type: ClaimType,
    @Enumerated(EnumType.STRING)
    var sensitivity: Sensitivity,
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

    fun toDomain(sources: List<ClaimSource>): Claim =
        Claim(ClaimId(id), WorkspaceId(workspaceId), text, type, sensitivity, sources, Revision(revision))

    fun apply(claim: Claim) {
        require(claim.id.value == id && claim.workspaceId.value == workspaceId) { "entity identity mismatch" }
        text = claim.text
        type = claim.type
        sensitivity = claim.sensitivity
        revision = claim.revision.value
    }

    companion object {
        fun from(claim: Claim) =
            ClaimEntity(
                claim.id.value,
                claim.workspaceId.value,
                claim.text,
                claim.type,
                claim.sensitivity,
                claim.revision.value,
            )
    }
}
