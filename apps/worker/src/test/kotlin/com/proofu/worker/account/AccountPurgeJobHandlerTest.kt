package com.proofu.worker.account

import com.proofu.worker.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class AccountPurgeJobHandlerTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `purges every row of the workspace including immutable snapshots and leaves tombstones`() {
        val s = AccountSeed.seed(jdbc)
        // Immutable rows resist an ordinary delete...
        assertThat(
            runCatching {
                jdbc.update("delete from submission_snapshots where application_id = ?", s.applicationId)
            }.isFailure,
        ).isTrue()
        // ...and the purge refuses a workspace that was not marked deleted.
        val early = enqueue(s)
        awaitStatus(early, "FAILED:PURGE_NOT_REQUESTED")

        jdbc.update("update users set deleted_at = now() where id = ?", s.userId)
        jdbc.update("update workspaces set deleted_at = now() where id = ?", s.workspaceId)
        val job = enqueue(s)
        awaitStatus(job, "SUCCEEDED")

        val w = s.workspaceId
        for (
        (table, sql) in
        listOf(
            "career_entries" to "select count(*) from career_entries where workspace_id = ?",
            "claims" to "select count(*) from claims where workspace_id = ?",
            "evidence" to "select count(*) from evidence where workspace_id = ?",
            "job_postings" to "select count(*) from job_postings where workspace_id = ?",
            "applications" to "select count(*) from applications where workspace_id = ?",
            "documents" to "select count(*) from documents where workspace_id = ?",
            "exports" to "select count(*) from exports where workspace_id = ?",
            "jobs" to "select count(*) from jobs where workspace_id = ? and type <> 'account.purge'",
            "members" to "select count(*) from workspace_members where workspace_id = ?",
        )
        ) {
            assertThat(jdbc.queryForObject(sql, Long::class.java, w)).describedAs(table).isZero()
        }
        assertThat(
            jdbc.queryForObject(
                "select count(*) from submission_snapshots where id = ?",
                Long::class.java,
                s.submissionId,
            ),
        ).isZero()
        assertThat(
            jdbc.queryForObject(
                "select count(*) from export_files where object_key = ?",
                Long::class.java,
                "pg:${s.exportId}",
            ),
        ).isZero()
        assertThat(
            jdbc.queryForObject("select count(*) from user_profiles where user_id = ?", Long::class.java, s.userId),
        ).isZero()

        val user =
            jdbc.queryForMap(
                "select email, display_name, oidc_subject, deleted_at from users where id = ?",
                s.userId,
            )
        assertThat(user["email"]).isEqualTo("deleted:${s.userId}@invalid")
        assertThat(user["oidc_subject"]).isEqualTo("deleted:${s.userId}")
        assertThat(user["display_name"]).isEqualTo("삭제된 사용자")
        assertThat(user["deleted_at"]).isNotNull()
        assertThat(
            jdbc.queryForMap("select name, deleted_at from workspaces where id = ?", w)["name"],
        ).isEqualTo("deleted")
        assertThat(
            jdbc.queryForObject(
                "select result -> 'deleted' ->> 'submission_snapshots' from jobs where id = ?",
                String::class.java,
                job,
            ),
        ).isEqualTo("1")

        // Re-delivery is harmless.
        val again = enqueue(s)
        awaitStatus(again, "SUCCEEDED")
    }

    private fun enqueue(s: AccountSeed.Seed): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            s.workspaceId,
            AccountPurgeJobHandler.TYPE,
            """{"userId":"${s.userId}","workspaceId":"${s.workspaceId}"}""",
        )
        return id
    }

    private fun awaitStatus(
        job: UUID,
        status: String,
    ) = await().atMost(Duration.ofSeconds(15)).untilAsserted {
        assertThat(
            jdbc.queryForObject(
                "select status || coalesce(':' || error_code, '') from jobs where id = ?",
                String::class.java,
                job,
            ),
        ).isEqualTo(status)
    }
}
