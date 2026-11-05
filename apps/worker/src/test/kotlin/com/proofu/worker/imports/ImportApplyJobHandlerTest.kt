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
class ImportApplyJobHandlerTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var planner: ImportPlanJobHandler

    @Autowired
    lateinit var applier: ImportApplyJobHandler

    @Autowired
    lateinit var mapper: ObjectMapper

    private fun bundle(company: String = "ACME"): String =
        """
        {
          "source": "career-hub",
          "items": [
            {"sourceKey": "acme-posting", "type": "POSTING", "sourcePath": "jd-analysis/acme.md",
             "fields": {"company": "$company", "roleTitle": "백엔드", "canonicalUrl": "https://jobs.test/1"}},
            {"sourceKey": "acme-apply", "type": "APPLICATION", "sourcePath": "APPLICATIONS.md#L42",
             "fields": {"company": "$company", "roleTitle": "백엔드", "status": "SUBMITTED",
                        "deadlineAt": "2026-09-20"},
             "links": {"posting": ["acme-posting"]}}
          ],
          "contents": {"acme-posting": "[자격 요건]\n- 경력 5년 이상"}
        }
        """.trimIndent()

    private fun plan(
        workspaceId: UUID,
        payload: String,
    ): String =
        mapper
            .readValue(
                planner.handle(JobRecord(UUID.randomUUID(), workspaceId, ImportPlanJobHandler.TYPE, payload, 0, 3)),
                Map::class.java,
            )["runId"]
            .toString()

    private fun apply(
        workspaceId: UUID,
        runId: String,
        keys: List<String>,
        payload: String,
    ): Map<*, *> {
        val items = mapper.readTree(payload)
        val applyPayload =
            mapper.writeValueAsString(
                mapOf(
                    "runId" to runId,
                    "sourceKeys" to keys,
                    "items" to mapper.treeToValue(items.get("items"), List::class.java),
                    "contents" to mapper.treeToValue(items.get("contents"), Map::class.java),
                ),
            )
        return mapper.readValue(
            applier.handle(JobRecord(UUID.randomUUID(), workspaceId, ImportApplyJobHandler.TYPE, applyPayload, 0, 3)),
            Map::class.java,
        )
    }

    @Test
    fun `a picked posting and application become real rows, and the ledger records what they became`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val payload = bundle()
        val runId = plan(w, payload)

        val result = apply(w, runId, listOf("acme-posting", "acme-apply"), payload)
        assertThat((result["applied"] as Map<*, *>).keys).contains("acme-posting", "acme-apply")
        assertThat(result["skipped"] as Map<*, *>).isEmpty()

        // The snapshot carries exactly the archive's text, marked as imported.
        val snapshot =
            jdbc.queryForMap(
                "select s.source, s.raw_text from job_posting_snapshots s " +
                    "join job_postings p on p.id = s.posting_id where p.workspace_id = ? and s.source = 'IMPORTED'",
                w,
            )
        assertThat(snapshot["source"]).isEqualTo("IMPORTED")
        assertThat(snapshot["raw_text"].toString()).contains("경력 5년 이상")

        // The application is at the status the archive recorded, not walked there.
        val application =
            jdbc.queryForMap(
                "select status, company, deadline_at from applications where workspace_id = ? and company = 'ACME'",
                w,
            )
        assertThat(application["status"]).isEqualTo("SUBMITTED")
        assertThat(application["deadline_at"]).isNotNull()
        assertThat(
            jdbc.queryForObject(
                "select count(*) from application_status_events where application_id in " +
                    "(select id from applications where workspace_id = ? and company = 'ACME')",
                Long::class.java,
                w,
            ),
        ).isZero()

        // Applying the same plan again changes nothing: the items are no longer NEW.
        val again = apply(w, runId, listOf("acme-posting", "acme-apply"), payload)
        assertThat(again["applied"] as Map<*, *>).isEmpty()
        assertThat((again["skipped"] as Map<*, *>).values.first().toString()).contains("이미 들여왔습니다")
    }

    @Test
    fun `a bundle that moved since the plan is refused, not applied from the preview`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val runId = plan(w, bundle())

        // Same keys, different content: the hash no longer matches what the plan recorded.
        val moved = apply(w, runId, listOf("acme-posting"), bundle(company = "ACME Corp"))
        assertThat(moved["applied"] as Map<*, *>).isEmpty()
        assertThat((moved["skipped"] as Map<*, *>)["acme-posting"].toString()).contains("내용이 바뀌었습니다")
        assertThat(
            jdbc.queryForObject(
                "select count(*) from job_posting_snapshots s join job_postings p on p.id = s.posting_id " +
                    "where p.workspace_id = ? and s.source = 'IMPORTED'",
                Long::class.java,
                w,
            ),
        ).isZero()
    }

    @Test
    fun `an application whose posting was not picked waits rather than inventing one`() {
        val w = AccountSeed.seed(jdbc).workspaceId
        val payload = bundle()
        val runId = plan(w, payload)

        val result = apply(w, runId, listOf("acme-apply"), payload)
        assertThat(result["applied"] as Map<*, *>).isEmpty()
        assertThat((result["skipped"] as Map<*, *>)["acme-apply"].toString()).contains("공고가 아직")
        assertThat(
            jdbc.queryForObject(
                "select count(*) from applications where workspace_id = ? and company = 'ACME'",
                Long::class.java,
                w,
            ),
        ).isZero()
    }
}
