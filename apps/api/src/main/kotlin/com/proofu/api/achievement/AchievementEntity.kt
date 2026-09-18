package com.proofu.api.achievement

import com.proofu.domain.career.Achievement
import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "achievements")
class AchievementEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "project_id")
    val projectId: UUID,
    @Column(columnDefinition = "text")
    var action: String,
    @Column(columnDefinition = "text")
    var outcome: String,
    @Column(name = "metric_value")
    var metricValue: BigDecimal?,
    @Column(name = "metric_unit")
    var metricUnit: String?,
    var baseline: String?,
    var timeframe: String?,
    var confidence: BigDecimal,
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

    fun apply(a: Achievement) {
        require(a.id.value == id && a.workspaceId.value == workspaceId && a.projectId.value == projectId) {
            "entity identity mismatch"
        }
        action = a.action
        outcome = a.outcome
        metricValue = a.metricValue
        metricUnit = a.metricUnit
        baseline = a.baseline
        timeframe = a.timeframe
        confidence = a.confidence.toColumn()
        revision = a.revision.value
    }

    fun toDomain(): Achievement =
        Achievement(
            id = AchievementId(id),
            workspaceId = WorkspaceId(workspaceId),
            projectId = ProjectId(projectId),
            action = action,
            outcome = outcome,
            confidence = Confidence(confidence.toDouble()),
            metricValue = metricValue,
            metricUnit = metricUnit,
            baseline = baseline,
            timeframe = timeframe,
            revision = Revision(revision),
        )

    companion object {
        fun from(a: Achievement): AchievementEntity =
            AchievementEntity(
                id = a.id.value,
                workspaceId = a.workspaceId.value,
                projectId = a.projectId.value,
                action = a.action,
                outcome = a.outcome,
                metricValue = a.metricValue,
                metricUnit = a.metricUnit,
                baseline = a.baseline,
                timeframe = a.timeframe,
                confidence = a.confidence.toColumn(),
                revision = a.revision.value,
            )

        /** numeric(3,2) column. */
        private fun Confidence.toColumn(): BigDecimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    }
}
