package com.proofu.api.review

import com.proofu.domain.applications.Review
import com.proofu.domain.applications.ReviewConfidence
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.ReviewId
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
@Table(name = "reviews")
class ReviewEntity(
    @Id
    val id: UUID,
    @Column(name = "application_id")
    val applicationId: UUID,
    @Column(name = "observed_fact", columnDefinition = "text")
    var observedFact: String,
    @Column(columnDefinition = "text")
    var hypothesis: String,
    @Column(columnDefinition = "text")
    var rationale: String?,
    @Enumerated(EnumType.STRING)
    var confidence: ReviewConfidence,
    @Column(name = "improvement_action", columnDefinition = "text")
    var improvementAction: String?,
    @Column(name = "verification_plan", columnDefinition = "text")
    var verificationPlan: String?,
    var version: Long,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun toDomain() =
        Review(
            ReviewId(id),
            ApplicationId(applicationId),
            observedFact,
            hypothesis,
            rationale,
            confidence,
            improvementAction,
            verificationPlan,
        )

    fun apply(r: Review) {
        require(r.id.value == id && r.applicationId.value == applicationId) { "entity identity mismatch" }
        observedFact = r.observedFact
        hypothesis = r.hypothesis
        rationale = r.rationale
        confidence = r.confidence
        improvementAction = r.improvementAction
        verificationPlan = r.verificationPlan
    }

    companion object {
        fun from(r: Review) =
            ReviewEntity(
                r.id.value,
                r.applicationId.value,
                r.observedFact,
                r.hypothesis,
                r.rationale,
                r.confidence,
                r.improvementAction,
                r.verificationPlan,
                version = 1,
            )
    }
}
