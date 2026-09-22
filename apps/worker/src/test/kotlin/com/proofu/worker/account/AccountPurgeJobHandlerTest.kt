package com.proofu.worker.account

import com.proofu.domain.jobs.ContentHash
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
        val s = seed()
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

    private data class Seed(
        val userId: UUID,
        val workspaceId: UUID,
        val applicationId: UUID,
        val submissionId: UUID,
        val exportId: UUID,
    )

    private fun seed(): Seed {
        val userId = UUID.randomUUID()
        val ws = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            "sub-$userId",
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", ws, userId)
        jdbc.update("insert into workspace_members (workspace_id, user_id, role) values (?, ?, 'OWNER')", ws, userId)
        jdbc.update(
            "insert into user_profiles (user_id, full_name, email) values (?, '홍길동', 'hong@example.com')",
            userId,
        )
        val entry = UUID.randomUUID()
        jdbc.update(
            "insert into career_entries (id, workspace_id, type, title, start_date) values (?, ?, 'EMPLOYMENT', 'PM', '2024-01-01')",
            entry,
            ws,
        )
        val project = UUID.randomUUID()
        jdbc.update(
            "insert into projects (id, workspace_id, career_entry_id, name, role, summary) values (?, ?, ?, 'P', 'r', 's')",
            project,
            ws,
            entry,
        )
        val claim = UUID.randomUUID()
        jdbc.update("insert into claims (id, workspace_id, text, claim_type) values (?, ?, 'c', 'FACT')", claim, ws)
        jdbc.update(
            "insert into claim_sources (claim_id, source_type, source_id, source_revision) values (?, 'PROJECT', ?, 1)",
            claim,
            project,
        )
        val evidence = UUID.randomUUID()
        jdbc.update(
            "insert into evidence (id, workspace_id, type, title, source, uri, captured_at) values (?, ?, 'URL', 'e', 'USER_INPUT', 'https://a', now())",
            evidence,
            ws,
        )
        jdbc.update(
            "insert into claim_evidence (claim_id, evidence_id, relation, confidence) values (?, ?, 'SUPPORTS', 0.9)",
            claim,
            evidence,
        )
        val posting = UUID.randomUUID()
        val snapshot = UUID.randomUUID()
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            posting,
            ws,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', 't', ?, now())",
            snapshot,
            posting,
            ContentHash.of("t"),
        )
        val requirement = UUID.randomUUID()
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at) values (?, ?, 'REQUIRED', 'r', 1, 'USER', 'APPROVED', now())",
            requirement,
            snapshot,
        )
        val application = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title, status) values (?, ?, ?, 'ABC', 'PM', 'SUBMITTED')",
            application,
            ws,
            snapshot,
        )
        jdbc.update(
            "insert into application_status_events (id, application_id, from_status, to_status, occurred_at) values (?, ?, null, 'INTERESTED', now())",
            UUID.randomUUID(),
            application,
        )
        jdbc.update(
            "insert into requirement_matches (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, user_decision) values (?, ?, ?, ?, 1, 50, 'MEDIUM', '{}'::jsonb, 'SUPPORTED', 'ACCEPTED')",
            UUID.randomUUID(),
            application,
            requirement,
            claim,
        )
        val document = UUID.randomUUID()
        jdbc.update(
            "insert into documents (id, workspace_id, application_id, type, title) values (?, ?, ?, 'RESUME', 'd')",
            document,
            ws,
            application,
        )
        val version = UUID.randomUUID()
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) values (?, ?, '{\"blocks\":[]}'::jsonb, 'ko-v1', 'USER')",
            version,
            document,
        )
        jdbc.update(
            "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, 'a-1', 'CLAIM', ?, 1, 'DERIVED_FROM')",
            version,
            claim,
        )
        val submission = UUID.randomUUID()
        jdbc.update(
            "insert into submission_snapshots (id, application_id, document_version_id, posting_snapshot_id, hash, submitted_at) values (?, ?, ?, ?, repeat('a', 64), now())",
            submission,
            application,
            version,
            snapshot,
        )
        jdbc.update(
            "insert into reviews (id, application_id, observed_fact, hypothesis) values (?, ?, 'f', 'h')",
            UUID.randomUUID(),
            application,
        )
        val export = UUID.randomUUID()
        jdbc.update(
            "insert into exports (id, workspace_id, document_version_id, format, template_version, status, object_key) values (?, ?, ?, 'DOCX', 'ko-v1', 'READY', ?)",
            export,
            ws,
            version,
            "pg:$export",
        )
        jdbc.update("insert into export_files (object_key, content) values (?, ?)", "pg:$export", "x".toByteArray())
        jdbc.update(
            "insert into jobs (id, workspace_id, type, status, payload, finished_at) values (?, ?, 'application.match', 'SUCCEEDED', '{}'::jsonb, now())",
            UUID.randomUUID(),
            ws,
        )
        jdbc.update(
            "insert into audit_events (id, workspace_id, actor_id, actor_type, action, target_type, target_id) values (?, ?, ?, 'USER', 'x', 'y', ?)",
            UUID.randomUUID(),
            ws,
            userId,
            ws,
        )
        return Seed(userId, ws, application, submission, export)
    }

    private fun enqueue(s: Seed): UUID {
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
