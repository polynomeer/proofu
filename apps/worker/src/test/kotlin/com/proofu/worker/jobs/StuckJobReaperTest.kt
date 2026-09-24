package com.proofu.worker.jobs

import com.proofu.worker.TestcontainersConfiguration
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * A worker that dies mid-job must not leave the user polling forever. The poller is parked for
 * this class so the state right after a reap can be read; jobs are handed to it deliberately.
 */
@SpringBootTest(
    properties = ["proofu.worker.poll-interval=1h", "proofu.worker.reaper-initial-delay=1h"],
)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class StuckJobReaperTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var reaper: StuckJobReaper

    @Autowired
    lateinit var properties: WorkerProperties

    @Autowired
    lateinit var registry: MeterRegistry

    @Autowired
    lateinit var poller: JobPoller

    private fun workspace(): UUID {
        val userId = UUID.randomUUID()
        val ws = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            "sub-$userId",
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", ws, userId)
        return ws
    }

    /** A job claimed [ageMinutes] ago and never finished, as a crashed worker leaves it. */
    private fun abandoned(
        workspace: UUID,
        attempts: Int,
        ageMinutes: Long,
        type: String = "stuck.type",
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into jobs (id, workspace_id, type, status, payload, attempts, max_attempts, started_at)
            values (?, ?, ?, 'RUNNING', '{}'::jsonb, ?, 3, now() - make_interval(mins => cast(? as int)))
            """.trimIndent(),
            id,
            workspace,
            type,
            attempts,
            ageMinutes,
        )
        return id
    }

    private fun status(id: UUID): String =
        jdbc.queryForObject(
            "select status || ':' || coalesce(error_code, '-') from jobs where id = ?",
            String::class.java,
            id,
        )!!

    @Test
    fun `a claim that outlives the lease goes back in the queue, and fails once attempts are spent`() {
        val ws = workspace()
        val lease = properties.jobLease.toMinutes()
        val fresh = abandoned(ws, attempts = 1, ageMinutes = 0)
        val lost = abandoned(ws, attempts = 1, ageMinutes = lease + 1)
        val spent = abandoned(ws, attempts = 3, ageMinutes = lease + 1)

        reaper.reap()

        // Still inside its lease: left alone.
        assertThat(status(fresh)).isEqualTo("RUNNING:-")
        assertThat(status(lost)).isEqualTo("QUEUED:LEASE_EXPIRED")
        assertThat(status(spent)).isEqualTo("FAILED:LEASE_EXPIRED")
        assertThat(
            jdbc.queryForObject("select started_at is null from jobs where id = ?", Boolean::class.java, lost),
        ).isTrue()
        assertThat(
            jdbc.queryForObject("select finished_at is not null from jobs where id = ?", Boolean::class.java, spent),
        ).isTrue()
        assertThat(
            registry
                .find("proofu.jobs.recovered")
                .tag("outcome", "requeued")
                .counter()
                ?.count(),
        ).isEqualTo(1.0)
        assertThat(
            registry
                .find("proofu.jobs.recovered")
                .tag("outcome", "failed")
                .counter()
                ?.count(),
        ).isEqualTo(1.0)

        // Requeued means really requeued: the poller picks it up and reports the unknown type.
        poller.poll()
        assertThat(status(lost)).isEqualTo("FAILED:UNKNOWN_JOB_TYPE")
        // Nothing left to reap.
        reaper.reap()
        assertThat(status(fresh)).isEqualTo("RUNNING:-")
    }

    @Test
    fun `a stuck job with no attempts left stops blocking the next request for the same work`() {
        val ws = workspace()
        val lease = properties.jobLease.toMinutes()
        // The API deduplicates against QUEUED and RUNNING rows, so a lost claim blocks the work.
        val spent = abandoned(ws, attempts = 3, ageMinutes = lease + 1, type = "account.export")
        val retrying = abandoned(ws, attempts = 1, ageMinutes = lease + 1, type = "document.export")
        assertThat(blocking(ws)).isEqualTo(2L)

        reaper.reap()

        // Attempts spent: the job is finished and the user can ask again.
        assertThat(status(spent)).isEqualTo("FAILED:LEASE_EXPIRED")
        // Attempts left: still queued on purpose — the work is pending, not abandoned.
        assertThat(status(retrying)).isEqualTo("QUEUED:LEASE_EXPIRED")
        assertThat(blocking(ws)).isEqualTo(1L)
    }

    private fun blocking(workspace: UUID): Long =
        jdbc.queryForObject(
            "select count(*) from jobs where workspace_id = ? and status in ('QUEUED', 'RUNNING')",
            Long::class.java,
            workspace,
        ) ?: 0L
}
