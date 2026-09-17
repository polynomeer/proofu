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
            return
        }
        try {
            val result = handler.handle(job)
            repository.succeed(job.id, result)
            log.info("Job {} ({}) succeeded on attempt {}", job.id, job.type, job.attempts)
        } catch (e: JobFailure) {
            log.warn("Job {} ({}) failed with {}: {}", job.id, job.type, e.errorCode, e.message)
            repository.fail(job, e.errorCode, e.retryable)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.error("Job {} ({}) crashed on attempt {}", job.id, job.type, job.attempts, e)
            repository.fail(job, "UNHANDLED_ERROR", retry = true)
        }
    }
}
