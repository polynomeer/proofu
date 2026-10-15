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
    fun `career log list walks an index`() {
        assertIndexed(
            "career_logs",
            """
            select * from career_logs where workspace_id = '$WORKSPACE' and deleted_at is null
            order by occurred_at desc, id desc limit 20
            """.trimIndent(),
        )
    }

    /** The screen's one filter: the logs still waiting to become an achievement (V19). */
    @Test
    fun `the unpromoted log list walks its partial index`() {
        assertIndexed(
            "career_logs",
            """
            select * from career_logs where workspace_id = '$WORKSPACE' and deleted_at is null
              and promoted_at is null
            order by occurred_at desc, id desc limit 20
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

    /**
     * The dashboard is the first screen of every session and the only one whose query counts
     * things. Counting them for the whole workspace instead of the five rows it shows cost 17
     * seconds at 2000 career entries — and an index on claim_sources did not help, because the
     * work was proportional to the workspace either way (V18 is for the other readers).
     */
    @Test
    fun `dashboard timeline counts only the entries it shows`() {
        // Not assertIndexed: this one legitimately sorts its five rows. What has to hold is
        // that the work does not grow with the workspace.
        val plan =
            plan(
                """
                with shown as (
                  select ce.id, ce.start_date from career_entries ce
                  where ce.workspace_id = '$WORKSPACE' and ce.deleted_at is null
                  order by ce.start_date desc, ce.id desc limit 5
                ),
                entry_claims as (
                  select s.id as entry_id, c.id as claim_id
                  from shown s
                  join claim_sources cs on
                       (cs.source_type = 'CAREER_ENTRY' and cs.source_id = s.id)
                    or (cs.source_type = 'PROJECT' and cs.source_id in
                          (select id from projects where career_entry_id = s.id and deleted_at is null))
                    or (cs.source_type = 'ACHIEVEMENT' and cs.source_id in
                          (select a.id from achievements a join projects p on p.id = a.project_id
                           where p.career_entry_id = s.id and a.deleted_at is null and p.deleted_at is null))
                  join claims c on c.id = cs.claim_id and c.deleted_at is null
                )
                select s.id,
                       (select count(*) from projects p where p.career_entry_id = s.id and p.deleted_at is null),
                       (select count(distinct claim_id) from entry_claims ec where ec.entry_id = s.id)
                from shown s order by s.start_date desc, s.id desc
                """.trimIndent(),
            )
        assertThat(rowsScanned(plan)).describedAs("rows read for the dashboard timeline:\n%s", plan).isLessThan(500)
        // Only the tables this workspace actually fills: a sequential scan of an empty table is
        // free and the planner is right to choose it.
        listOf("career_entries", "projects").forEach { table ->
            assertThat(plan).describedAs("plan:\n%s", plan).doesNotContain("Seq Scan on $table")
        }
    }

    companion object {
        /** One workspace with more rows than any early user will have, so plans are realistic. */
        private const val ROWS = 20_000
        private val WORKSPACE: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000ff")
        private val USER: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000fe")

        /** A neighbour holding as much again: without one, scanning the table looks cheap. */
        private val NEIGHBOUR: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000fd")
        private val NEIGHBOUR_USER: UUID = UUID.fromString("00000000-0000-7000-8000-0000000000fc")
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
            jdbc.update(
                "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'perf')",
                NEIGHBOUR_USER,
                "perf-$NEIGHBOUR_USER",
                "$NEIGHBOUR_USER@example.com",
            )
            jdbc.update(
                "insert into workspaces (id, owner_user_id, name) values (?, ?, 'perf-neighbour')",
                NEIGHBOUR,
                NEIGHBOUR_USER,
            )
            jdbc.execute(
                """
                insert into career_entries (workspace_id, type, title, start_date)
                select '$WORKSPACE', 'EMPLOYMENT', 'entry ' || g, date '2000-01-01' + g
                from generate_series(1, $ROWS) g
                """.trimIndent(),
            )
            jdbc.execute(
                """
                -- One project per career entry. Hanging every project off the same entry would
                -- make projects.career_entry_id useless to the planner and the plans below
                -- would be measuring the seeding, not the schema.
                insert into projects (workspace_id, career_entry_id, name, role, summary, start_date)
                select '$WORKSPACE', ce.id, 'project ' || ce.ord, 'role', 'summary', date '2000-01-01' + ce.ord::int
                from (select id, row_number() over (order by id) as ord
                      from career_entries where workspace_id = '$WORKSPACE') ce
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
                insert into career_logs (workspace_id, occurred_at, body)
                select '$WORKSPACE', date '2000-01-01' + g, 'log ' || g
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
            // One row nobody else looks like: search behaviour on a distinctive term (V16's
            // trigram indexes are for exactly this) is exercised by SearchApiTest, which cares
            // about results; plans for substring search depend on the planner's own cost
            // estimates and are not asserted here.
            jdbc.update(
                "insert into career_entries (workspace_id, type, title, start_date) values (?, 'EMPLOYMENT', '코틀린특이값 엔지니어', date '2020-01-01')",
                WORKSPACE,
            )
            jdbc.update(
                "insert into skills (workspace_id, canonical_name, category) values (?, '코틀린특이값', 'PROGRAMMING_LANGUAGE')",
                WORKSPACE,
            )
            jdbc.update(
                """
                insert into evidence (workspace_id, type, title, source, uri, captured_at)
                values (?, 'URL', '코틀린특이값 대시보드', 'USER_INPUT', 'https://example.test/one', now())
                """.trimIndent(),
                WORKSPACE,
            )
            listOf(
                "career_entries" to "type, title, start_date",
                "skills" to "canonical_name, category, created_at",
                "evidence" to "type, title, source, uri, captured_at",
                "capabilities" to "name, definition, category, created_at",
                "career_logs" to "occurred_at, body",
            ).forEach { (table, columns) ->
                jdbc.execute(
                    """
                    insert into $table (workspace_id, $columns)
                    select '$NEIGHBOUR', $columns from $table where workspace_id = '$WORKSPACE'
                    """.trimIndent(),
                )
            }
            listOf(
                "career_entries",
                "projects",
                "evidence",
                "skills",
                "capabilities",
                "career_logs",
                "job_postings",
                "applications",
                "documents",
            ).forEach { jdbc.execute("analyze $it") }
        }
    }
}
