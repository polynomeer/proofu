package com.proofu.worker.jobs

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class JobRepository(
    private val jdbc: JdbcTemplate,
) {
    /**
     * Atomically claims up to [limit] queued jobs whose schedule has elapsed. SKIP LOCKED lets
     * several worker instances poll the same table without contention.
     */
    @Transactional
    fun claim(limit: Int): List<JobRecord> =
        jdbc.query(
            """
            update jobs set status = 'RUNNING', started_at = now(), attempts = attempts + 1
            where id in (
                select id from jobs
                where status = 'QUEUED' and scheduled_at <= now()
                order by scheduled_at
                for update skip locked
                limit ?
            )
            returning id, workspace_id, type, payload::text, attempts, max_attempts
            """.trimIndent(),
            { rs, _ ->
                JobRecord(
                    id = rs.getObject("id", UUID::class.java),
                    workspaceId = rs.getObject("workspace_id", UUID::class.java),
                    type = rs.getString("type"),
                    payload = rs.getString("payload"),
                    attempts = rs.getInt("attempts"),
                    maxAttempts = rs.getInt("max_attempts"),
                )
            },
            limit,
        )

    fun succeed(
        id: UUID,
        result: String?,
    ) {
        jdbc.update(
            "update jobs set status = 'SUCCEEDED', result = ?::jsonb, error_code = null, finished_at = now() where id = ?",
            result,
            id,
        )
    }

    /** Re-queues with backoff when retries remain, otherwise marks the job FAILED. */
    fun fail(
        job: JobRecord,
        errorCode: String,
        retry: Boolean,
    ) {
        if (retry && job.retriesLeft) {
            jdbc.update(
                "update jobs set status = 'QUEUED', error_code = ?, scheduled_at = now() + make_interval(secs => ?) where id = ?",
                errorCode,
                backoffSeconds(job.attempts),
                job.id,
            )
        } else {
            jdbc.update(
                "update jobs set status = 'FAILED', error_code = ?, finished_at = now() where id = ?",
                errorCode,
                job.id,
            )
        }
    }

    private fun backoffSeconds(attempt: Int): Int = minOf(BACKOFF_BASE_SECONDS shl (attempt - 1), BACKOFF_MAX_SECONDS)

    private companion object {
        const val BACKOFF_BASE_SECONDS = 5
        const val BACKOFF_MAX_SECONDS = 300
    }
}
