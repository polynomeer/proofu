package com.proofu.api.career

import com.proofu.domain.career.CareerEntry
import com.proofu.domain.career.CareerEntryStatus
import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
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
import java.time.LocalDate
import java.util.UUID

/** Persistence shape of [CareerEntry]. The domain object is the validated form; this class only stores it. */
@Entity
@Table(name = "career_entries")
class CareerEntryEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Enumerated(EnumType.STRING)
    var type: CareerEntryType,
    var title: String,
    var organization: String?,
    var location: String?,
    @Column(columnDefinition = "text")
    var description: String?,
    @Column(name = "start_date")
    var startDate: LocalDate,
    @Column(name = "end_date")
    var endDate: LocalDate?,
    @Enumerated(EnumType.STRING)
    var visibility: Visibility,
    @Enumerated(EnumType.STRING)
    var status: CareerEntryStatus,
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

    fun toDomain(): CareerEntry =
        CareerEntry(
            id = CareerEntryId(id),
            workspaceId = WorkspaceId(workspaceId),
            type = type,
            title = title,
            startDate = startDate,
            endDate = endDate,
            organization = organization,
            location = location,
            description = description,
            visibility = visibility,
            status = status,
            revision = Revision(revision),
        )

    fun apply(entry: CareerEntry) {
        require(entry.id.value == id && entry.workspaceId.value == workspaceId) { "entity identity mismatch" }
        type = entry.type
        title = entry.title
        organization = entry.organization
        location = entry.location
        description = entry.description
        startDate = entry.startDate
        endDate = entry.endDate
        visibility = entry.visibility
        status = entry.status
        revision = entry.revision.value
    }

    companion object {
        fun from(entry: CareerEntry): CareerEntryEntity =
            CareerEntryEntity(
                id = entry.id.value,
                workspaceId = entry.workspaceId.value,
                type = entry.type,
                title = entry.title,
                organization = entry.organization,
                location = entry.location,
                description = entry.description,
                startDate = entry.startDate,
                endDate = entry.endDate,
                visibility = entry.visibility,
                status = entry.status,
                revision = entry.revision.value,
            )
    }
}
