package com.proofu.worker.matching

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
@Import(TestcontainersConfiguration::class, ApplicationMatchJobHandlerTest.ScriptedModel::class)
@ActiveProfiles("test")
class ApplicationMatchJobHandlerTest {
    @TestConfiguration(proxyBeanMethods = false)
    class ScriptedModel {
        @Bean
        fun modelClient(): ModelClient = FakeModelClient()
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var model: ModelClient

    @Test
    fun `scores approved requirements against eligible claims, explains the top ones and keeps user decisions`() {
        val s = seed()
        val fake = model as FakeModelClient
        fake.enqueue(
            explanation(
                """{"requirementId":"${s.reqSaas}","claimId":"${s.claimSaas}","reason":"SaaS 온보딩 재설계가 제품 기획 경험을 뒷받침합니다.","matchedRequirementPhrase":"SaaS 제품 기획","matchedEvidencePhrase":"SaaS 온보딩"}""",
            ),
        )

        val job = enqueue(s.workspaceId, s.applicationId)
        awaitStatus(job, "SUCCEEDED")

        val rows =
            jdbc.queryForList(
                "select id, requirement_id, claim_id, rank, score, band, reason, claim_status_at_scoring from requirement_matches where application_id = ? order by requirement_id, rank",
                s.applicationId,
            )
        // The confidential claim is never a candidate; the unrelated claim scores below the floor for the SaaS requirement.
        assertThat(rows.map { it["claim_id"] }).doesNotContain(s.claimSecret)
        val saasRows = rows.filter { it["requirement_id"] == s.reqSaas }
        assertThat(saasRows.first()["claim_id"]).isEqualTo(s.claimSaas)
        assertThat(saasRows.first()["band"]).isEqualTo("HIGH")
        assertThat(saasRows.first()["reason"]).isEqualTo("SaaS 온보딩 재설계가 제품 기획 경험을 뒷받침합니다.")
        assertThat(saasRows.first()["claim_status_at_scoring"]).isEqualTo("SUPPORTED")
        val result =
            jdbc.queryForMap(
                "select result ->> 'sensitiveSkipped' as skipped, result ->> 'explained' as explained from jobs where id = ?",
                job,
            )
        assertThat(result["skipped"]).isEqualTo("1")
        assertThat(result["explained"]).isEqualTo("1")
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).doesNotContain("claim:${s.claimSecret}")

        // The user rejects the top candidate; a re-run must keep that decision and the row.
        val matchId =
            saasRows.first()["id"]
                ?: jdbc.queryForObject(
                    "select id from requirement_matches where application_id = ? and claim_id = ?",
                    UUID::class.java,
                    s.applicationId,
                    s.claimSaas,
                )
        jdbc.update("update requirement_matches set user_decision = 'REJECTED' where id = ?", matchId)
        fake.enqueue(explanation())
        val second = enqueue(s.workspaceId, s.applicationId)
        awaitStatus(second, "SUCCEEDED")
        val after =
            jdbc.queryForMap(
                "select user_decision, reason, run_job_id from requirement_matches where id = ?",
                matchId,
            )
        assertThat(after["user_decision"]).isEqualTo("REJECTED")
        assertThat(after["reason"]).isEqualTo("SaaS 온보딩 재설계가 제품 기획 경험을 뒷받침합니다.") // kept when the new run has none
        assertThat(after["run_job_id"]).isEqualTo(second)

        // With the workspace's AI consent, the confidential claim becomes a candidate and reaches the model.
        jdbc.update(
            "insert into workspace_settings (workspace_id, ai_consent, ai_consent_at) values (?, 'CONFIDENTIAL', now())",
            s.workspaceId,
        )
        fake.enqueue(explanation())
        val third = enqueue(s.workspaceId, s.applicationId)
        awaitStatus(third, "SUCCEEDED")
        assertThat(
            jdbc.queryForObject(
                "select result ->> 'sensitiveSkipped' from jobs where id = ?",
                String::class.java,
                third,
            ),
        ).isEqualTo("0")
        assertThat(
            fake.requests
                .last()
                .documents
                .map { it.sourceId },
        ).contains("claim:${s.claimSecret}")
    }

    private fun explanation(vararg items: String) =
        ModelOutcome.Completed(
            """{"explanations":[${items.joinToString(",")}]}""",
            "claude-opus-5",
            ModelUsage(500, 100, 0, 0),
            false,
        )

    private data class Seed(
        val workspaceId: UUID,
        val applicationId: UUID,
        val reqSaas: UUID,
        val claimSaas: UUID,
        val claimSecret: UUID,
    )

    private fun seed(): Seed {
        val userId = UUID.randomUUID()
        val ws = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", ws, userId)
        val postingId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        val text = "SaaS 제품 기획 경험\nKotlin 5년"
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            postingId,
            ws,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', ?, ?, now())",
            snapshotId,
            postingId,
            text,
            ContentHash.of(text),
        )
        val reqSaas = UUID.randomUUID()
        val reqKotlin = UUID.randomUUID()
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at, sort_order) values (?, ?, 'REQUIRED', 'SaaS 제품 기획 경험', 1, 'USER', 'APPROVED', now(), 1)",
            reqSaas,
            snapshotId,
        )
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at, sort_order) values (?, ?, 'SKILL', 'Kotlin 5년', 1, 'USER', 'APPROVED', now(), 2)",
            reqKotlin,
            snapshotId,
        )
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, sort_order) values (?, ?, 'SKILL', 'Draft only', 1, 'AI', 'DRAFT', 3)",
            UUID.randomUUID(),
            snapshotId,
        )
        val applicationId = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title) values (?, ?, ?, 'ABC', 'PM')",
            applicationId,
            ws,
            snapshotId,
        )

        // Career chain: entry -> project (recent) -> achievement with metric, claim about it, verified evidence.
        val entry = UUID.randomUUID()
        val project = UUID.randomUUID()
        val achievement = UUID.randomUUID()
        jdbc.update(
            "insert into career_entries (id, workspace_id, type, title, start_date) values (?, ?, 'EMPLOYMENT', 'PM', '2024-01-01')",
            entry,
            ws,
        )
        jdbc.update(
            "insert into projects (id, workspace_id, career_entry_id, name, role, summary, start_date) values (?, ?, ?, 'SaaS 온보딩 재설계', 'PM', '온보딩 개선', '2026-03-01')",
            project,
            ws,
            entry,
        )
        jdbc.update(
            "insert into achievements (id, workspace_id, project_id, action, outcome, metric_value, metric_unit, confidence) values (?, ?, ?, 'SaaS 온보딩 재설계 주도', '활성화율 상승', 40, '%', 0.9)",
            achievement,
            ws,
            project,
        )
        val claimSaas = claim(ws, "SaaS 제품 기획을 주도해 온보딩을 재설계", "INTERNAL", "ACHIEVEMENT", achievement)
        val evidence = UUID.randomUUID()
        jdbc.update(
            "insert into evidence (id, workspace_id, type, title, source, uri, verification, captured_at) values (?, ?, 'URL', 'Q2 대시보드', 'USER_INPUT', 'https://a', 'USER_VERIFIED', now())",
            evidence,
            ws,
        )
        jdbc.update(
            "insert into claim_evidence (claim_id, evidence_id, relation, confidence) values (?, ?, 'SUPPORTS', 0.9)",
            claimSaas,
            evidence,
        )
        val claimSecret = claim(ws, "SaaS 제품 기획 기밀 프로젝트", "CONFIDENTIAL", "PROJECT", project)
        claim(ws, "사내 봉사활동 참여", "INTERNAL", "CAREER_ENTRY", entry)
        return Seed(ws, applicationId, reqSaas, claimSaas, claimSecret)
    }

    private fun claim(
        ws: UUID,
        text: String,
        sensitivity: String,
        sourceType: String,
        sourceId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into claims (id, workspace_id, text, claim_type, sensitivity) values (?, ?, ?, 'FACT', ?)",
            id,
            ws,
            text,
            sensitivity,
        )
        jdbc.update(
            "insert into claim_sources (claim_id, source_type, source_id, source_revision) values (?, ?, ?, 1)",
            id,
            sourceType,
            sourceId,
        )
        return id
    }

    private fun enqueue(
        workspaceId: UUID,
        applicationId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            workspaceId,
            ApplicationMatchJobHandler.TYPE,
            """{"applicationId":"$applicationId"}""",
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
