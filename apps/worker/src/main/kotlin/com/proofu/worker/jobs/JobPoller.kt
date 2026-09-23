package com.proofu.worker.jobs

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Polls the jobs table and dispatches each claimed job to the handler registered
 * for its type. Handlers run sequentially inside one poll; scale out by running
 * more worker instances.
 */
@Component
class JobPoller(
    private val repository: JobRepository,
    private val properties: WorkerProperties,
    private val metrics: JobMetrics,
    handlers: List<JobHandler>,
) {
    private val log = LoggerFactory.getLogger(JobPoller::class.java)
    private val handlersByType = handlers.associateBy { it.type }

    init {
        require(handlersByType.size == handlers.size) { "duplicate job handler types: ${handlers.map { it.type }}" }
        log.info("Job handlers registered: {}", handlersByType.keys.sorted())
    }

    @Scheduled(fixedDelayString = "\${proofu.worker.poll-interval}")
    fun poll() {
        val jobs = repository.claim(properties.batchSize)
        jobs.forEach(::run)
    }

    fun run(job: JobRecord) {
        val handler = handlersByType[job.type]
        if (handler == null) {
            log.error("No handler for job type {} (jobId={})", job.type, job.id)
            repository.fail(job, "UNKNOWN_JOB_TYPE", retry = false)
            metrics.completed(job.type, "failed", 0)
            return
        }
        val startedAt = System.nanoTime()
        try {
            val result = handler.handle(job)
            repository.succeed(job.id, result)
            log.info("Job {} ({}) succeeded on attempt {}", job.id, job.type, job.attempts)
            metrics.completed(job.type, "succeeded", System.nanoTime() - startedAt)
        } catch (e: JobFailure) {
            log.warn("Job {} ({}) failed with {}: {}", job.id, job.type, e.errorCode, e.message)
            repository.fail(job, e.errorCode, e.retryable)
            metrics.completed(job.type, outcomeOf(job, e.retryable), System.nanoTime() - startedAt)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.error("Job {} ({}) crashed on attempt {}", job.id, job.type, job.attempts, e)
            repository.fail(job, "UNHANDLED_ERROR", retry = true)
            metrics.completed(job.type, outcomeOf(job, retryable = true), System.nanoTime() - startedAt)
        }
    }

    /** A job that will be tried again is not a failure yet; only the last attempt counts as one. */
    private fun outcomeOf(
        job: JobRecord,
        retryable: Boolean,
    ) = if (retryable && job.retriesLeft) "retrying" else "failed"
}
