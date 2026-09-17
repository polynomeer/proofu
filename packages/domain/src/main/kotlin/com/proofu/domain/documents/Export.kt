package com.proofu.domain.documents

import com.proofu.domain.common.InvalidStatusTransition

enum class ExportFormat {
    DOCX,
    PDF,
    MARKDOWN,
    JSON,
}

/** REQUESTED -> RENDERING -> VALIDATING -> READY | FAILED | EXPIRED */
enum class ExportStatus {
    REQUESTED,
    RENDERING,
    VALIDATING,
    READY,
    FAILED,
    EXPIRED,
    ;

    val allowedTransitions: Set<ExportStatus>
        get() =
            when (this) {
                REQUESTED -> setOf(RENDERING, FAILED)
                RENDERING -> setOf(VALIDATING, FAILED)
                VALIDATING -> setOf(READY, FAILED)
                READY -> setOf(EXPIRED)
                FAILED, EXPIRED -> emptySet()
            }

    fun transitionTo(target: ExportStatus): ExportStatus =
        if (target in allowedTransitions) target else throw InvalidStatusTransition(this, target)
}
