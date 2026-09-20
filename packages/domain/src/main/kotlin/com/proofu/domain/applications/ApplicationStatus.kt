package com.proofu.domain.applications

import com.proofu.domain.common.InvalidStatusTransition

/**
 * Application lifecycle (domain §4.5). This enum is the single source of truth for
 * allowed transitions; the API, worker and UI must not duplicate the table.
 */
enum class ApplicationStatus {
    INTERESTED,
    PREPARING,
    SUBMITTED,
    DOCUMENT_PASSED,
    DOCUMENT_REJECTED,
    NO_RESPONSE,
    WITHDRAWN,
    HANDOFF_READY,
    HANDED_OFF_TO_ITERVIEW,
    REVIEW_PENDING,
    REVIEWED,
    ;

    val allowedTransitions: Set<ApplicationStatus>
        get() =
            when (this) {
                INTERESTED -> setOf(PREPARING, WITHDRAWN)
                PREPARING -> setOf(SUBMITTED, WITHDRAWN)
                SUBMITTED -> setOf(DOCUMENT_PASSED, DOCUMENT_REJECTED, NO_RESPONSE, WITHDRAWN)
                DOCUMENT_PASSED -> setOf(HANDOFF_READY)
                HANDOFF_READY -> setOf(HANDED_OFF_TO_ITERVIEW)
                DOCUMENT_REJECTED, NO_RESPONSE -> setOf(REVIEW_PENDING)
                REVIEW_PENDING -> setOf(REVIEWED)
                HANDED_OFF_TO_ITERVIEW, REVIEWED, WITHDRAWN -> emptySet()
            }

    fun canTransitionTo(target: ApplicationStatus): Boolean = target in allowedTransitions

    fun transitionTo(target: ApplicationStatus): ApplicationStatus =
        if (canTransitionTo(target)) target else throw InvalidStatusTransition(this, target)

    /** Terminal states are excluded from "in progress" dashboards. */
    val isTerminal: Boolean get() = allowedTransitions.isEmpty()

    /** Handoff to iterview requires DOCUMENT_PASSED lineage and explicit consent (checked by the caller). */
    val isHandoffEligible: Boolean get() = this == DOCUMENT_PASSED || this == HANDOFF_READY

    /**
     * A submission snapshot can be frozen while preparing (moving the application to SUBMITTED)
     * or, as a correction, while already SUBMITTED. Nothing later accepts one.
     */
    val acceptsSubmission: Boolean
        get() = this == INTERESTED || this == PREPARING || this == SUBMITTED

    /** A retrospective only makes sense once a document result (or silence) is in. */
    val acceptsReview: Boolean
        get() = this == DOCUMENT_REJECTED || this == NO_RESPONSE || this == REVIEW_PENDING || this == REVIEWED
}
