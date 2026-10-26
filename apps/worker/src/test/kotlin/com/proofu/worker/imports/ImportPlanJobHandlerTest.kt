package com.proofu.worker.imports

import com.proofu.worker.TestcontainersConfiguration
import com.proofu.worker.account.AccountSeed
import com.proofu.worker.jobs.JobRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class ImportPlanJobHandlerTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var handler: ImportPlanJobHandler

    @Autowired
    lateinit var mapper: ObjectMapper

    private fun bundle(
        applicationHash: String = "ACME",
        source: String = "career-hub",
    ): String =
        """
        {
          "source": "$source",
          "items": [
            {"sourceKey": "acme-posting", "type": "POSTING", "sourcePath": "jd-analysis/acme.md",
             "fields": {"company": "$applicationHash"}},
            {"sourceKey": "acme-apply", "type": "APPLICATION", "sourcePath": "APPLICATIONS.md#L42",
             "fields": {"company": "$applicationHash", "roleTitle": "백엔드", "status": "SUBMITTED"},
             "links": {"posting": ["acme-posting"]}},
            {"sourceKey": "no-posting", "type": "APPLICATION",
             "fields": {"company": "B", "roleTitle": "r", "status": "SUBMITTED"}}
          ],
          "contents": {"acme-posting": "[자격 요건] 경력 5년 이상"}
        }
        """.trimIndent()

    private fun plan(
        workspaceId: UUID,
        payload: String,
    ): Map<*, *> {
        val result =
            handler.handle(JobRecord(UUID.randomUUID(), workspaceId, ImportPlanJobHandler.TYPE, payload, 0, 3))
        return mapper.readValue(result, Map::class.java)
    }

    private fun statusOf(
        runId: String,
        key: String,
    ): String =
        jdbc.queryForObject(
            "select status from import_items where run_id = cast(? as uuid) and source_key = ?",
            String::class.java,
            runId,
            key,
        )!!

    @Test
    fun `a plan writes the ledger and nothing else, and says why an item was refused`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val applicationsBefore =
            jdbc.queryForObject(
                "select count(*) from applications where workspace_id = ?",
                Long::class.java,
                w,
            )

        val runId = plan(w, bundle())["runId"].toString()

        // The workspace is untouched: a plan is a question, not a change.
        assertThat(jdbc.queryForObject("select count(*) from applications where workspace_id = ?", Long::class.java, w))
            .isEqualTo(applicationsBefore)
        assertThat(statusOf(runId, "acme-posting")).isEqualTo("NEW")
        assertThat(statusOf(runId, "acme-apply")).isEqualTo("NEW")
        // A past application with no posting cannot be recorded, and the row says so.
        assertThat(statusOf(runId, "no-posting")).isEqualTo("REJECTED")
        assertThat(
            jdbc.queryForObject(
                "select reason from import_items where run_id = cast(? as uuid) and source_key = 'no-posting'",
                String::class.java,
                runId,
            ),
        ).contains("공고가 없습니다")
        assertThat(
            jdbc.queryForObject(
                "select item_count from import_runs where id = cast(? as uuid)",
                Int::class.java,
                runId,
            ),
        ).isEqualTo(3)
    }

    @Test
    fun `the same bundle twice is not two imports, and a changed one says so`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val first = plan(w, bundle())["runId"].toString()

        // Pretend the user applied the posting from the first run.
        jdbc.update(
            "update import_items set applied_at = now(), target_type = 'posting_snapshot', " +
                "target_id = gen_random_uuid() " +
                "where run_id = cast(? as uuid) and source_key = 'acme-posting'",
            first,
        )

        val again = plan(w, bundle())["runId"].toString()
        assertThat(statusOf(again, "acme-posting")).isEqualTo("UNCHANGED")

        // The archive moved on: same key, different content.
        val moved = plan(w, bundle(applicationHash = "ACME Corp"))["runId"].toString()
        assertThat(statusOf(moved, "acme-posting")).isEqualTo("CHANGED")
    }

    @Test
    fun `the hash is the server's, so an adapter cannot decide what counts as unchanged`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val first = plan(w, bundle())["runId"].toString()
        val hash =
            jdbc.queryForObject(
                "select content_hash from import_items where run_id = cast(? as uuid) and source_key = 'acme-posting'",
                String::class.java,
                first,
            )!!
        // Same content sent again hashes the same, whatever the sender did or did not send.
        val second = plan(w, bundle())["runId"].toString()
        assertThat(
            jdbc.queryForObject(
                "select content_hash from import_items where run_id = cast(? as uuid) and source_key = 'acme-posting'",
                String::class.java,
                second,
            ),
        ).isEqualTo(hash)
        assertThat(hash).matches("[0-9a-f]{64}")
    }
}
