package com.proofu.worker.retention

import com.proofu.worker.TestcontainersConfiguration
import com.proofu.worker.account.AccountSeed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class RetentionSweeperTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sweeper: RetentionSweeper

    private fun count(
        table: String,
        where: String,
        vararg args: Any,
    ): Long = jdbc.queryForObject("select count(*) from $table where $where", Long::class.java, *args) ?: 0L

    @Test
    fun `purges expired trash from the leaves up, keeps what live rows still cite, and honours the window`() {
        val s = AccountSeed.seed(jdbc)
        val w = s.workspaceId
        jdbc.update(
            "insert into workspace_settings (workspace_id, trash_retention_days, export_retention_days) values (?, 7, 1)",
            w,
        )
        // Everything sits in the trash for 10 days: past a 7-day window, inside a 30-day one.
        val old = "now() - interval '10 days'"
        for (t in listOf("achievements", "projects", "career_entries", "claims", "evidence")) {
            jdbc.update("update $t set deleted_at = $old where workspace_id = ?", w)
        }
        jdbc.update(
            "update requirements set deleted_at = $old where snapshot_id in (select id from job_posting_snapshots where posting_id in (select id from job_postings where workspace_id = ?))",
            w,
        )
        // Immutable rows referencing the claim stay: provenance_links (no FK) and the submission snapshot.
        val provenanceBefore =
            count("provenance_links", "source_id in (select id from claims where workspace_id = ?)", w)
        assertThat(provenanceBefore).isEqualTo(1L)

        val first = sweeper.sweep(w)
        assertThat(first).containsEntry("claims", 1).containsEntry("evidence", 1).containsEntry("requirements", 1)
        assertThat(
            first,
        ).containsEntry("achievements", 1).containsEntry("projects", 1).containsEntry("career_entries", 1)
        assertThat(first).containsEntry("claim_requirement_matches", 1)
        assertThat(count("claims", "workspace_id = ?", w)).isZero()
        assertThat(count("claim_sources", "claim_id not in (select id from claims)")).isZero()
        assertThat(count("requirement_matches", "application_id = ?", s.applicationId)).isZero()
        assertThat(count("projects", "workspace_id = ?", w)).isZero()
        assertThat(count("career_entries", "workspace_id = ?", w)).isZero()
        // Untouched: postings, applications, documents and their immutable children.
        assertThat(count("job_postings", "workspace_id = ?", w)).isOne()
        assertThat(count("applications", "workspace_id = ?", w)).isOne()
        assertThat(count("submission_snapshots", "id = ?", s.submissionId)).isOne()
        assertThat(
            count(
                "provenance_links",
                "version_id in (select id from document_versions where document_id in (select id from documents where workspace_id = ?))",
                w,
            ),
        ).isEqualTo(provenanceBefore)
        assertThat(
            count("audit_events", "workspace_id = ? and action = 'retention.swept' and actor_type = 'SYSTEM'", w),
        ).isOne()

        // Idempotent: nothing left to do.
        assertThat(sweeper.sweep(w)).isEmpty()
    }

    @Test
    fun `a source still cited by a live claim waits in the trash until the claim goes`() {
        val s = AccountSeed.seed(jdbc)
        val w = s.workspaceId
        jdbc.update("insert into workspace_settings (workspace_id, trash_retention_days) values (?, 7)", w)
        jdbc.update("update projects set deleted_at = now() - interval '10 days' where workspace_id = ?", w)
        jdbc.update("update achievements set deleted_at = now() - interval '10 days' where workspace_id = ?", w)
        // The claim is alive and sources the project → project (and its achievement chain) stays.
        val kept = sweeper.sweep(w)
        assertThat(kept).doesNotContainKey("projects")
        assertThat(count("projects", "workspace_id = ?", w)).isOne()
        // Achievement is not cited → gone; project still blocked by the claim source.
        assertThat(count("achievements", "workspace_id = ?", w)).isZero()

        jdbc.update("update claims set deleted_at = now() - interval '10 days' where workspace_id = ?", w)
        val second = sweeper.sweep(w)
        assertThat(second).containsEntry("claims", 1).containsEntry("projects", 1)
        assertThat(count("projects", "workspace_id = ?", w)).isZero()
    }

    @Test
    fun `inside the window nothing is purged`() {
        val s = AccountSeed.seed(jdbc)
        val w = s.workspaceId
        jdbc.update("update claims set deleted_at = now() - interval '3 days' where workspace_id = ?", w)
        assertThat(sweeper.sweep(w)).isEmpty()
        assertThat(count("claims", "workspace_id = ?", w)).isOne()
    }

    @Test
    fun `export files expire by the policy and are deleted only when no READY row shares them`() {
        val s = AccountSeed.seed(jdbc)
        val w = s.workspaceId
        jdbc.update("insert into workspace_settings (workspace_id, export_retention_days) values (?, 2)", w)
        // The seeded export is fresh; a second, older export shares the same file.
        val older = UUID.randomUUID()
        jdbc.update(
            """
            insert into exports (id, workspace_id, document_version_id, format, template_version, status, object_key, created_at)
            select ?, workspace_id, document_version_id, format, template_version, 'READY', object_key, now() - interval '3 days'
            from exports where id = ?
            """.trimIndent(),
            older,
            s.exportId,
        )
        val account = UUID.randomUUID()
        jdbc.update(
            "insert into export_files (object_key, content) values (?, ?)",
            "pg:account:$account",
            "zip".toByteArray(),
        )
        jdbc.update(
            "insert into account_exports (id, workspace_id, status, object_key, expires_at) values (?, ?, 'READY', ?, now() - interval '1 hour')",
            account,
            w,
            "pg:account:$account",
        )

        val counts = sweeper.sweep(w)
        assertThat(
            counts,
        ).containsEntry("exports", 1).containsEntry("account_exports", 1).containsEntry("export_files", 1)
        assertThat(
            jdbc.queryForObject("select status from exports where id = ?", String::class.java, older),
        ).isEqualTo("EXPIRED")
        assertThat(
            jdbc.queryForObject("select status from exports where id = ?", String::class.java, s.exportId),
        ).isEqualTo("READY")
        // Shared file survives for the fresh READY row; the account ZIP is gone.
        assertThat(count("export_files", "object_key = ?", "pg:${s.exportId}")).isOne()
        assertThat(count("export_files", "object_key = ?", "pg:account:$account")).isZero()
        assertThat(
            jdbc.queryForObject("select status from account_exports where id = ?", String::class.java, account),
        ).isEqualTo("EXPIRED")

        // Once the fresh export ages past the window the shared file goes too.
        jdbc.update("update exports set created_at = now() - interval '3 days' where id = ?", s.exportId)
        assertThat(sweeper.sweep(w)).containsEntry("exports", 1).containsEntry("export_files", 1)
        assertThat(count("export_files", "object_key = ?", "pg:${s.exportId}")).isZero()
    }
}
