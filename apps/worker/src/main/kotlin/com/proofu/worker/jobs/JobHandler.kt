package com.proofu.worker.jobs

/**
 * One handler per job type. Implementations must be idempotent: a job may be
 * re-delivered after a crash between execution and status update.
 */
interface JobHandler {
    val type: String

    /** Returns the JSON result stored on the job, or null when there is nothing to record. */
    fun handle(job: JobRecord): String?
}

class JobFailure(
    val errorCode: String,
    message: String,
    /** When false the job is marked FAILED immediately regardless of remaining attempts. */
    val retryable: Boolean = true,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
