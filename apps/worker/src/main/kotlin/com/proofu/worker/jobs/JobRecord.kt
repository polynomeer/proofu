package com.proofu.worker.jobs

import java.util.UUID

enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** A claimed row from the jobs table (ADR-0004). */
data class JobRecord(
    val id: UUID,
    val workspaceId: UUID,
    val type: String,
    val payload: String,
    val attempts: Int,
    val maxAttempts: Int,
) {
    val retriesLeft: Boolean get() = attempts < maxAttempts
}
