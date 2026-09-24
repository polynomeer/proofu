package com.proofu.worker.jobs

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * A worker that dies mid-job leaves the row RUNNING forever: nobody retries it, the user polls a
 * job that will never finish, and `JobService.enqueue` keeps deduplicating against it. Once a
 * claim outlives the lease the job is presumed lost and goes back in the queue, or fails when
 * its attempts are spent (docs/architecture/adr/0004-async-jobs.md).
 */
@Component
class StuckJobReaper(
    private val jdbc: JdbcTemplate,
    private val properties: WorkerProperties,
    private val metrics: JobMetrics,
) {
    private val log = LoggerFactory.getLogger(StuckJobReaper::class.java)

    @Scheduled(
        fixedDelayString = "\${proofu.worker.reaper-interval:1m}",
        initialDelayString = "\${proofu.worker.reaper-initial-delay:30s}",
    )
    fun reap() {
        val seconds = properties.jobLease.seconds
        val recovered =
            jdbc.query(
                """
                update jobs
                set status = case when attempts < max_attempts then 'QUEUED' else 'FAILED' end,
                    error_code = ?,
                    started_at = null,
                    scheduled_at = case when attempts < max_attempts then now() else scheduled_at end,
                    finished_at = case when attempts < max_attempts then null else now() end
                where status = 'RUNNING' and started_at < now() - make_interval(secs => ?)
                returning id, type, attempts, max_attempts, status
                """.trimIndent(),
                { rs, _ ->
                    Recovered(
                        id = rs.getObject("id", UUID::class.java),
                        type = rs.getString("type"),
                        attempts = rs.getInt("attempts"),
                        maxAttempts = rs.getInt("max_attempts"),
                        requeued = rs.getString("status") == "QUEUED",
                    )
                },
                ERROR_CODE,
                seconds,
            )
        recovered.forEach {
            log.warn(
                "Job {} ({}) outlived the {}s lease on attempt {}/{}; {}",
                it.id,
                it.type,
                seconds,
                it.attempts,
                it.maxAttempts,
                if (it.requeued) "requeued" else "failed",
            )
            metrics.recovered(it.type, if (it.requeued) "requeued" else "failed")
        }
    }

    private data class Recovered(
        val id: UUID,
        val type: String,
        val attempts: Int,
        val maxAttempts: Int,
        val requeued: Boolean,
    )

    companion object {
        /** Error code carried by a job whose worker never reported back. */
        const val ERROR_CODE = "LEASE_EXPIRED"
    }
}
