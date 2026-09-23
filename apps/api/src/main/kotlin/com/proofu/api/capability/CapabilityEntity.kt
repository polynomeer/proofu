package com.proofu.api.capability

import com.proofu.domain.career.Capability
import com.proofu.domain.career.CapabilityCategory
import com.proofu.domain.career.ProficiencyLevel
import com.proofu.domain.common.CapabilityId
import com.proofu.domain.common.Revision
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
@Table(name = "capabilities")
class CapabilityEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "parent_id")
    var parentId: UUID?,
    var name: String,
    @Column(columnDefinition = "text")
    var definition: String,
    @Enumerated(EnumType.STRING)
    var category: CapabilityCategory,
    @Enumerated(EnumType.STRING)
    @Column(name = "self_assessed_level")
    var selfAssessedLevel: ProficiencyLevel?,
    @Column(name = "evidence_criteria", columnDefinition = "text")
    var evidenceCriteria: String?,
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

    fun apply(capability: Capability) {
        require(capability.id.value == id && capability.workspaceId.value == workspaceId) {
            "entity identity mismatch"
        }
        parentId = capability.parentId?.value
        name = capability.name
        definition = capability.definition
        category = capability.category
        selfAssessedLevel = capability.selfAssessedLevel
        evidenceCriteria = capability.evidenceCriteria
        revision = capability.revision.value
    }

    fun toDomain(): Capability =
        Capability(
            id = CapabilityId(id),
            workspaceId = WorkspaceId(workspaceId),
            name = name,
            definition = definition,
            category = category,
            selfAssessedLevel = selfAssessedLevel,
            evidenceCriteria = evidenceCriteria,
            parentId = parentId?.let(::CapabilityId),
            revision = Revision(revision),
        )

    companion object {
        fun from(capability: Capability): CapabilityEntity =
            CapabilityEntity(
                id = capability.id.value,
                workspaceId = capability.workspaceId.value,
                parentId = capability.parentId?.value,
                name = capability.name,
                definition = capability.definition,
                category = capability.category,
                selfAssessedLevel = capability.selfAssessedLevel,
                evidenceCriteria = capability.evidenceCriteria,
                revision = capability.revision.value,
            )
    }
}
