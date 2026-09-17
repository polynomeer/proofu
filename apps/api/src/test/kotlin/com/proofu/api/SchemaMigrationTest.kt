package com.proofu.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** Verifies that Flyway applies migrations/ and that database-level invariants hold. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class SchemaMigrationTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var tx: TransactionTemplate

    /** Each rejected statement runs in its own transaction so PostgreSQL's abort state does not leak. */
    private fun attempt(
        sql: String,
        vararg args: Any,
    ) {
        tx.execute { jdbc.update(sql, *args) }
    }

    @Test
    fun `core tables exist after migration`() {
        val tables =
            jdbc
                .queryForList(
                    "select table_name from information_schema.tables where table_schema = 'public'",
                    String::class.java,
                ).toSet()
        assertThat(tables).contains(
            "users",
            "workspaces",
            "career_entries",
            "projects",
            "achievements",
            "claims",
            "evidence",
            "claim_evidence",
            "job_postings",
            "job_posting_snapshots",
            "requirements",
            "applications",
            "application_status_events",
            "documents",
            "document_versions",
            "provenance_links",
            "submission_snapshots",
            "reviews",
            "jobs",
            "exports",
            "ai_executions",
            "audit_events",
        )
    }

    @Test
    fun `submission snapshots cannot be updated or deleted`() {
        val seed = seedWorkspaceWithPosting()
        val applicationId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val submissionId = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title) values (?, ?, ?, 'ABC', 'PM')",
            applicationId,
            seed.workspaceId,
            seed.snapshotId,
        )
        jdbc.update(
            "insert into documents (id, workspace_id, application_id, type, title) values (?, ?, ?, 'RESUME', 'Resume')",
            documentId,
            seed.workspaceId,
            applicationId,
        )
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) " +
                "values (?, ?, '{}'::jsonb, 'v1', 'USER')",
            versionId,
            documentId,
        )
        jdbc.update(
            "insert into submission_snapshots " +
                "(id, application_id, document_version_id, posting_snapshot_id, hash, submitted_at) " +
                "values (?, ?, ?, ?, ?, now())",
            submissionId,
            applicationId,
            versionId,
            seed.snapshotId,
            "a".repeat(64),
        )

        assertThatThrownBy {
            attempt(
                "update submission_snapshots set hash = ? where id = ?",
                "b".repeat(64),
                submissionId,
            )
        }.isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("immutable")
        assertThatThrownBy { attempt("delete from submission_snapshots where id = ?", submissionId) }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("immutable")
    }

    @Test
    fun `career entry end date must not precede start date`() {
        val seed = seedWorkspaceWithPosting()
        assertThatThrownBy {
            attempt(
                "insert into career_entries (id, workspace_id, type, title, start_date, end_date) " +
                    "values (?, ?, 'EMPLOYMENT', 'x', '2024-03-01', '2024-02-01')",
                UUID.randomUUID(),
                seed.workspaceId,
            )
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("career_entries_period_check")
    }

    @Test
    fun `achievement metric requires a unit`() {
        val seed = seedWorkspaceWithPosting()
        val careerEntryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        jdbc.update(
            "insert into career_entries (id, workspace_id, type, title, start_date) values (?, ?, 'EMPLOYMENT', 'x', '2024-01-01')",
            careerEntryId,
            seed.workspaceId,
        )
        jdbc.update(
            "insert into projects (id, workspace_id, career_entry_id, name, role, summary) values (?, ?, ?, 'p', 'r', 's')",
            projectId,
            seed.workspaceId,
            careerEntryId,
        )
        assertThatThrownBy {
            attempt(
                "insert into achievements (id, workspace_id, project_id, action, outcome, confidence, metric_value) " +
                    "values (?, ?, ?, 'a', 'o', 0.5, 40)",
                UUID.randomUUID(),
                seed.workspaceId,
                projectId,
            )
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("achievements_metric_unit_check")
    }

    private data class Seed(
        val workspaceId: UUID,
        val snapshotId: UUID,
    )

    private fun seedWorkspaceWithPosting(): Seed {
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val postingId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'https://issuer.test', ?, 'Tester')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'Personal')", workspaceId, userId)
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            postingId,
            workspaceId,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) " +
                "values (?, ?, 'MANUAL_TEXT', 'text', ?, now())",
            snapshotId,
            postingId,
            "c".repeat(64),
        )
        return Seed(workspaceId, snapshotId)
    }
}
