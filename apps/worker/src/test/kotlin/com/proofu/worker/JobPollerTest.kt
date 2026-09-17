package com.proofu.worker

import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, JobPollerTest.Handlers::class)
@ActiveProfiles("test")
class JobPollerTest {
    @TestConfiguration(proxyBeanMethods = false)
    class Handlers {
        @Bean
        fun echo(): JobHandler =
            object : JobHandler {
                override val type = "echo"

                override fun handle(job: JobRecord): String = job.payload
            }

        @Bean
        fun alwaysFails(): JobHandler =
            object : JobHandler {
                override val type = "always-fails"

                override fun handle(job: JobRecord): String = throw JobFailure("BOOM", "boom", retryable = true)
            }
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `queued job is claimed, handled and marked succeeded with its result`() {
        val jobId = enqueue("echo", """{"hello":"world"}""")

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val row = jdbc.queryForMap("select status, result::text as result, attempts from jobs where id = ?", jobId)
            assertThat(row["status"]).isEqualTo("SUCCEEDED")
            assertThat(row["attempts"]).isEqualTo(1)
            assertThat(row["result"] as String).contains("\"hello\"")
        }
    }

    @Test
    fun `unknown job types fail without retry`() {
        val jobId = enqueue("no-such-type", "{}")

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val row = jdbc.queryForMap("select status, error_code from jobs where id = ?", jobId)
            assertThat(row["status"]).isEqualTo("FAILED")
            assertThat(row["error_code"]).isEqualTo("UNKNOWN_JOB_TYPE")
        }
    }

    @Test
    fun `retryable failures are re-queued with backoff until attempts run out`() {
        val jobId = enqueue("always-fails", "{}", maxAttempts = 1)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val row = jdbc.queryForMap("select status, error_code, attempts from jobs where id = ?", jobId)
            assertThat(row["status"]).isEqualTo("FAILED")
            assertThat(row["error_code"]).isEqualTo("BOOM")
            assertThat(row["attempts"]).isEqualTo(1)
        }
    }

    private fun enqueue(
        type: String,
        payload: String,
        maxAttempts: Int = 3,
    ): UUID {
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'https://issuer.test', ?, 'Tester')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'Personal')", workspaceId, userId)
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload, max_attempts) values (?, ?, ?, ?::jsonb, ?)",
            jobId,
            workspaceId,
            type,
            payload,
            maxAttempts,
        )
        return jobId
    }
}
