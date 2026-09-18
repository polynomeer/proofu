package com.proofu.worker.analysis

import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.jobs.ContentHash
import com.proofu.worker.TestcontainersConfiguration
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
@Import(TestcontainersConfiguration::class, PostingAnalysisJobHandlerTest.ScriptedModel::class)
@ActiveProfiles("test")
class PostingAnalysisJobHandlerTest {
    @TestConfiguration(proxyBeanMethods = false)
    class ScriptedModel {
        @Bean
        fun modelClient(): ModelClient = FakeModelClient()
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var model: ModelClient

    private val text = "[자격 요건]\n- 경력 5년 이상\n- SaaS 제품 기획 경험"

    @Test
    fun `extracted requirements are stored as AI drafts and re-runs replace unreviewed drafts only`() {
        val (workspaceId, snapshotId) = seedSnapshot()
        val source = "snapshot:$snapshotId"
        val fake = model as FakeModelClient
        fake.enqueue(
            reply(
                """{"category":"REQUIRED","text":"경력 5년 이상","quote":"경력 5년 이상","confidence":0.9,"sourceId":"$source"}""",
                """{"category":"REQUIRED","text":"SaaS 제품 기획 경험","quote":"SaaS 제품 기획 경험","confidence":0.8,"sourceId":"$source"}""",
            ),
        )
        // A requirement the user already wrote must survive re-analysis.
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at, sort_order) values (?, ?, 'SKILL', 'SQL', 1.0, 'USER', 'APPROVED', now(), 1)",
            UUID.randomUUID(),
            snapshotId,
        )

        val first = enqueue(workspaceId, snapshotId)
        awaitStatus(first, "SUCCEEDED")
        val result =
            jdbc.queryForMap(
                "select result ->> 'extracted' as extracted, result ->> 'withoutSpan' as without_span from jobs where id = ?",
                first,
            )
        assertThat(result["extracted"]).isEqualTo("2")
        assertThat(result["without_span"]).isEqualTo("0")
        assertThat(drafts(snapshotId)).containsExactly("경력 5년 이상", "SaaS 제품 기획 경험")
        assertThat(
            jdbc.queryForObject(
                "select span_start from requirements where text = '경력 5년 이상' and deleted_at is null",
                Int::class.java,
            ),
        ).isEqualTo(10)

        // Approve one draft, then re-run with a different answer: the approved one stays, the other draft is replaced.
        jdbc.update(
            "update requirements set status = 'APPROVED', approved_at = now() where text = '경력 5년 이상' and deleted_at is null",
        )
        fake.enqueue(
            reply("""{"category":"PREFERRED","text":"새 항목","quote":"SaaS","confidence":0.5,"sourceId":"$source"}"""),
        )
        val second = enqueue(workspaceId, snapshotId)
        awaitStatus(second, "SUCCEEDED")

        val live =
            jdbc.queryForList(
                "select text, status, origin from requirements where snapshot_id = ? and deleted_at is null order by sort_order",
                snapshotId,
            )
        assertThat(live.map { it["text"] }).containsExactly("SQL", "경력 5년 이상", "새 항목")
        assertThat(live.map { it["status"] }).containsExactly("APPROVED", "APPROVED", "DRAFT")
        assertThat(
            jdbc.queryForObject("select count(*) from ai_executions where job_id = ?", Int::class.java, second),
        ).isEqualTo(1)
    }

    @Test
    fun `schema violations fail the job with AI_OUTPUT_INVALID and retry`() {
        val (workspaceId, snapshotId) = seedSnapshot()
        val fake = model as FakeModelClient
        fake.enqueue(
            ModelOutcome.Completed(
                """{"items":[{"category":"WRONG"}]}""",
                "claude-opus-5",
                ModelUsage(10, 5, 0, 0),
                false,
            ),
        )
        fake.enqueue(ModelOutcome.Completed("""{"items":[]}""", "claude-opus-5", ModelUsage(10, 5, 0, 0), false))

        val job = enqueue(workspaceId, snapshotId, maxAttempts = 2)
        awaitStatus(job, "SUCCEEDED")
        assertThat(jdbc.queryForObject("select attempts from jobs where id = ?", Int::class.java, job)).isEqualTo(2)
        assertThat(
            jdbc.queryForList(
                "select status from ai_executions where job_id = ? order by created_at",
                String::class.java,
                job,
            ),
        ).containsExactly("REJECTED_BY_VALIDATION", "SUCCEEDED")
    }

    private fun reply(vararg items: String) =
        ModelOutcome.Completed(
            """{"items":[${items.joinToString(",")}]}""",
            "claude-opus-5",
            ModelUsage(500, 100, 0, 0),
            false,
        )

    private fun drafts(snapshotId: UUID) =
        jdbc.queryForList(
            "select text from requirements where snapshot_id = ? and origin = 'AI' and status = 'DRAFT' and deleted_at is null order by sort_order",
            String::class.java,
            snapshotId,
        )

    private fun enqueue(
        workspaceId: UUID,
        snapshotId: UUID,
        maxAttempts: Int = 3,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload, max_attempts) values (?, ?, ?, ?::jsonb, ?)",
            id,
            workspaceId,
            PostingAnalysisJobHandler.TYPE,
            """{"snapshotId":"$snapshotId"}""",
            maxAttempts,
        )
        return id
    }

    private fun awaitStatus(
        job: UUID,
        status: String,
    ) = await().atMost(Duration.ofSeconds(15)).untilAsserted {
        assertThat(
            jdbc.queryForObject("select status from jobs where id = ?", String::class.java, job),
        ).isEqualTo(status)
    }

    private fun seedSnapshot(): Pair<UUID, UUID> {
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val postingId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", workspaceId, userId)
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            postingId,
            workspaceId,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', ?, ?, now())",
            snapshotId,
            postingId,
            text,
            ContentHash.of(text),
        )
        return workspaceId to snapshotId
    }
}
