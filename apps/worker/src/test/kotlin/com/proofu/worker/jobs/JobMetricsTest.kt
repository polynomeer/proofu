package com.proofu.worker.jobs

import com.proofu.ai.model.ModelProviderException
import com.proofu.ai.model.ProviderFailure
import com.proofu.worker.TestcontainersConfiguration
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.UUID

/** The queue SLIs of docs/operations/monitoring.md come from these meters. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, JobMetricsTest.BillingBlockedHandler::class)
@ActiveProfiles("test")
class JobMetricsTest {
    /** Stands in for an AI handler whose provider says the account is out of credit. */
    @Component
    class BillingBlockedHandler : JobHandler {
        override val type = "test.billing.blocked"

        override fun handle(job: JobRecord): String? =
            AiFailures.guard { throw ModelProviderException("credit", failure = ProviderFailure.BILLING) }
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var registry: MeterRegistry

    @Autowired
    lateinit var metrics: JobMetrics

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

    private fun counter(
        type: String,
        outcome: String,
    ) = registry
        .find("proofu.jobs.completed")
        .tag("type", type)
        .tag("outcome", outcome)
        .counter()
        ?.count() ?: 0.0

    @Test
    fun `an unknown job type counts as failed and the queue gauges follow the table`() {
        val ws = workspace()
        val before = counter("nonexistent.type", "failed")
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, 'nonexistent.type', '{}'::jsonb)",
            id,
            ws,
        )
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(
                jdbc.queryForObject("select status from jobs where id = ?", String::class.java, id),
            ).isEqualTo("FAILED")
        }
        assertThat(counter("nonexistent.type", "failed")).isEqualTo(before + 1)
        assertThat(registry.find("proofu.jobs.duration").tag("type", "nonexistent.type").timer()).isNotNull()

        // A job waiting in the queue shows up in the gauges on the next refresh.
        jdbc.update(
            "insert into jobs (id, workspace_id, type, status, payload, scheduled_at) values (?, ?, 'account.export', 'QUEUED', '{}'::jsonb, now() - interval '90 seconds')",
            UUID.randomUUID(),
            ws,
        )
        jdbc.update("update jobs set status = 'QUEUED', scheduled_at = now() + interval '1 hour' where id = ?", id)
        metrics.refresh()
        assertThat(registry.get("proofu.jobs.queued").gauge().value()).isGreaterThanOrEqualTo(1.0)
        assertThat(registry.get("proofu.jobs.oldest.age.seconds").gauge().value()).isGreaterThanOrEqualTo(60.0)
        assertThat(registry.get("proofu.jobs.running").gauge().value()).isGreaterThanOrEqualTo(0.0)
    }

    @Test
    fun `a failure only a person can clear is counted on its own, not just as a failed job`() {
        val ws = workspace()
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, 'test.billing.blocked', '{}'::jsonb)",
            id,
            ws,
        )
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(
                jdbc.queryForObject("select status from jobs where id = ?", String::class.java, id),
            ).isEqualTo("FAILED")
        }
        assertThat(
            registry
                .find("proofu.ai.operator.actions")
                .tag("code", "AI_BILLING_BLOCKED")
                .counter()
                ?.count(),
        ).isEqualTo(1.0)
        // A rate limit is retried, so it must not wake anyone.
        assertThat(registry.find("proofu.ai.operator.actions").tag("code", "AI_RATE_LIMITED").counter()).isNull()
    }
}
