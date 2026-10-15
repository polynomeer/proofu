package com.proofu.api.careerlog

import com.proofu.domain.career.CareerLog
import com.proofu.domain.career.CareerLogLink
import com.proofu.domain.career.CareerLogPromotion
import com.proofu.domain.career.CareerLogPromotionType
import com.proofu.domain.common.CareerLogId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
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

/**
 * `career_logs` (V19). The links live in `career_log_links` and are read and replaced as a set by
 * [CareerLogService] rather than mapped as a collection, the way project skills are.
 */
@Entity
@Table(name = "career_logs")
class CareerLogEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "occurred_at")
    var occurredAt: LocalDate,
    var body: String,
    @Enumerated(EnumType.STRING)
    var sensitivity: Sensitivity,
    @Column(name = "promoted_achievement_id")
    var promotedAchievementId: UUID? = null,
    @Column(name = "promoted_evidence_id")
    var promotedEvidenceId: UUID? = null,
    @Column(name = "promoted_at")
    var promotedAt: Instant? = null,
    var revision: Long = 1,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun apply(log: CareerLog) {
        require(log.id.value == id && log.workspaceId.value == workspaceId) { "entity identity mismatch" }
        occurredAt = log.occurredAt
        body = log.body
        sensitivity = log.sensitivity
        promotedAt = log.promotion?.promotedAt
        val target = log.promotion?.target
        promotedAchievementId = target?.id?.takeIf { target.type == CareerLogPromotionType.ACHIEVEMENT }
        promotedEvidenceId = target?.id?.takeIf { target.type == CareerLogPromotionType.EVIDENCE }
        revision = log.revision.value
    }

    /** [links] come from `career_log_links`, which the entity does not map. */
    fun toDomain(links: List<CareerLogLink> = emptyList()): CareerLog =
        CareerLog(
            id = CareerLogId(id),
            workspaceId = WorkspaceId(workspaceId),
            occurredAt = occurredAt,
            body = body,
            sensitivity = sensitivity,
            links = links,
            promotion = promotion(),
            revision = Revision(revision),
        )

    /** The promotion as the domain states it: the date may outlive the record it points at. */
    fun promotion(): CareerLogPromotion? =
        promotedAt?.let { at ->
            val target =
                promotedAchievementId?.let {
                    CareerLogPromotion.Target(CareerLogPromotionType.ACHIEVEMENT, it)
                } ?: promotedEvidenceId?.let {
                    CareerLogPromotion.Target(CareerLogPromotionType.EVIDENCE, it)
                }
            CareerLogPromotion(at, target)
        }

    companion object {
        fun from(log: CareerLog): CareerLogEntity =
            CareerLogEntity(
                id = log.id.value,
                workspaceId = log.workspaceId.value,
                occurredAt = log.occurredAt,
                body = log.body,
                sensitivity = log.sensitivity,
                revision = log.revision.value,
            ).also { it.apply(log) }
    }
}
