package com.proofu.api.performance

import com.proofu.api.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Every list screen pages with a keyset, so its ORDER BY must be an index the planner can walk;
 * otherwise the database sorts the whole workspace to return twenty rows. These plans are
 * checked against a workspace with enough rows that a sequential scan is never the cheap option.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ListQueryPlanTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun plan(sql: String): String =
        jdbc
            .queryForList("explain (analyze, format text) $sql")
            .joinToString("\n") { it.values.first().toString() }

    /** The planner may only reach the rows through an index, and must not sort them itself. */
    private fun assertIndexed(
        table: String,
        sql: String,
    ) {
        val plan = plan(sql)
        // Reading 20 rows may not touch the whole table: before V15 these plans sorted all
        // 20_000 rows of the workspace (4–18 ms each, growing with the workspace).
        assertThat(rowsScanned(plan)).describedAs("rows read for %s:\n%s", table, plan).isLessThan(500)
        assertThat(plan).describedAs("plan for %s:\n%s", table, plan).doesNotContain("Seq Scan on $table")
        assertThat(plan).describedAs("plan for %s:\n%s", table, plan).doesNotContain("Sort Method")
    }

    /** The largest "rows=" the plan reports, i.e. how much the database had to look at. */
    private fun rowsScanned(plan: String): Int =
        Regex("actual time=[0-9.]+\\.\\.[0-9.]+ rows=(\\d+)")
            .findAll(plan)
            .map { it.groupValues[1].toInt() }
            .maxOrNull() ?: 0

    @Test
    fun `career entry list walks an index`() {
        assertIndexed(
            "career_entries",
            """
            select * from career_entries where workspace_id = '$WORKSPACE' and deleted_at is null
            order by start_date desc, id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `project list walks an index`() {
        assertIndexed(
            "projects",
            """
            select * from projects where workspace_id = '$WORKSPACE' and deleted_at is null
            order by coalesce(start_date, date '9999-12-31') desc, id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `evidence list walks an index`() {
        assertIndexed(
            "evidence",
            """
            select * from evidence where workspace_id = '$WORKSPACE' and deleted_at is null
            order by captured_at desc, id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `skill list walks an index`() {
        assertIndexed(
            "skills",
            """
            select * from skills where workspace_id = '$WORKSPACE' and deleted_at is null
            order by created_at desc, id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `capability list walks an index`() {
        assertIndexed(
            "capabilities",
            """
            select * from capabilities where workspace_id = '$WORKSPACE' and deleted_at is null
            order by created_at desc, id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `posting list walks an index`() {
        assertIndexed(
            "job_postings",
            """
            select * from job_postings p where p.workspace_id = '$WORKSPACE' and p.deleted_at is null
            order by p.updated_at desc, p.id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `document list walks an index`() {
        assertIndexed(
            "documents",
            """
            select * from documents d where d.workspace_id = '$WORKSPACE' and d.deleted_at is null
            order by d.updated_at desc, d.id desc limit 20
            """.trimIndent(),
        )
    }

    @Test
    fun `application board walks an index`() {
        assertIndexed(
            "applications",
            """
            select * from applications where workspace_id = '$WORKSPACE' and deleted_at is null
            order by updated_at desc, id desc limit 20
            """.trimIndent(),
        )
    }

    companion object {
        /** One workspace with more rows than any early user will have, so plans are realistic. */
        private const val ROWS = 20_000
        private val WORKSPACE: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000ff")
        private val USER: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000fe")
        private var seeded = false

        @JvmStatic
        @BeforeAll
        fun seed(
            @Autowired jdbc: JdbcTemplate,
        ) {
            if (seeded) return
            seeded = true
            jdbc.update(
                "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'perf')",
                USER,
                "perf-$USER",
                "$USER@example.com",
            )
            jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'perf')", WORKSPACE, USER)
            jdbc.execute(
                """
                insert into career_entries (workspace_id, type, title, start_date)
                select '$WORKSPACE', 'EMPLOYMENT', 'entry ' || g, date '2000-01-01' + g
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into projects (workspace_id, career_entry_id, name, role, summary, start_date)
                select '$WORKSPACE', (select id from career_entries where workspace_id = '$WORKSPACE' limit 1),
                       'project ' || g, 'role', 'summary', date '2000-01-01' + g
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into evidence (workspace_id, type, title, source, uri, captured_at)
                select '$WORKSPACE', 'URL', 'evidence ' || g, 'USER_INPUT', 'https://example.test/' || g,
                       now() - (g || ' minutes')::interval
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into skills (workspace_id, canonical_name, category, created_at)
                select '$WORKSPACE', 'skill ' || g, 'TOOL', now() - (g || ' minutes')::interval
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into capabilities (workspace_id, name, definition, category, created_at)
                select '$WORKSPACE', 'capability ' || g, 'does things', 'EXECUTION', now() - (g || ' minutes')::interval
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into job_postings (workspace_id, company, role_title, updated_at)
                select '$WORKSPACE', 'company ' || g, 'role ' || g, now() - (g || ' minutes')::interval
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into job_posting_snapshots (posting_id, source, raw_text, content_hash, captured_at)
                select id, 'MANUAL_TEXT', 'text', md5(id::text) || md5(id::text), now()
                from job_postings where workspace_id = '$WORKSPACE'
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into applications (workspace_id, snapshot_id, company, role_title, status, updated_at)
                select '$WORKSPACE', s.id, 'company', 'role', 'INTERESTED', now() - (row_number() over () || ' minutes')::interval
                from job_posting_snapshots s join job_postings p on p.id = s.posting_id
                where p.workspace_id = '$WORKSPACE'
                """.trimIndent(),
            )
            jdbc.execute(
                """
                insert into documents (workspace_id, application_id, type, title, updated_at)
                select '$WORKSPACE', a.id, 'RESUME', 'document', now() - (row_number() over () || ' minutes')::interval
                from applications a where a.workspace_id = '$WORKSPACE'
                """.trimIndent(),
            )
            listOf(
                "career_entries",
                "projects",
                "evidence",
                "skills",
                "capabilities",
                "job_postings",
                "applications",
                "documents",
            ).forEach { jdbc.execute("analyze $it") }
        }
    }
}
