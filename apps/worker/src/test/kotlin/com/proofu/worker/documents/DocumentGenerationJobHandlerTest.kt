package com.proofu.worker.documents

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
@Import(TestcontainersConfiguration::class, DocumentGenerationJobHandlerTest.ScriptedModel::class)
@ActiveProfiles("test")
class DocumentGenerationJobHandlerTest {
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
    fun `drafts a version from accepted claims only, pins provenance and leaves approval to the user`() {
        val s = seed()
        val fake = model as FakeModelClient
        fake.enqueue(
            completed(
                """{"blocks":[
                  {"section":"motivation","text":"SaaS 제품을 만들고 싶습니다.","claimRefs":[],"evidenceRefs":[],"requirementRefs":["${s.requirement}"],"certainty":"UNSUPPORTED"},
                  {"section":"experience","text":"온보딩을 재설계해 활성화율을 40% 개선했습니다.","claimRefs":["${s.acceptedClaim}"],"evidenceRefs":["${s.evidence}"],"requirementRefs":["${s.requirement}"],"certainty":"SUPPORTED"},
                  {"section":"experience","text":"지어낸 경험입니다.","claimRefs":["${s.rejectedClaim}"],"evidenceRefs":[],"requirementRefs":[],"certainty":"SUPPORTED"}
                ]}""",
            ),
        )

        val job = enqueue(s.workspaceId, s.documentId)
        awaitStatus(job, "SUCCEEDED")

        // Only accepted claims were offered to the model.
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).containsExactly("requirements", "claim:${s.acceptedClaim}")

        val version =
            jdbc.queryForMap(
                "select id, parent_id, created_by, template_version, prompt_version, source_job_id, execution_id, content_json::text as content from document_versions where document_id = ?",
                s.documentId,
            )
        assertThat(version["created_by"]).isEqualTo("AI")
        assertThat(version["parent_id"]).isNull()
        assertThat(version["template_version"]).isEqualTo("ko-v1")
        assertThat(version["prompt_version"]).isEqualTo("draft-v1")
        assertThat(version["source_job_id"]).isEqualTo(job)
        assertThat(version["execution_id"]).isNotNull()
        val content = version["content"] as String
        assertThat(content).contains("\"motivation-1\"").contains("\"experience-1\"").doesNotContain("지어낸")
        assertThat(content).doesNotContain("\"approvedByUser\": true").doesNotContain("\"approvedByUser\":true")

        val links =
            jdbc.queryForList(
                "select block_id, source_type, source_id, source_revision, relation from provenance_links where version_id = ? order by block_id, source_type",
                version["id"],
            )
        assertThat(links.map { "${it["block_id"]}:${it["source_type"]}:${it["relation"]}" })
            .containsExactlyInAnyOrder(
                "motivation-1:REQUIREMENT:ADDRESSES",
                "experience-1:ACHIEVEMENT:DERIVED_FROM",
                "experience-1:CLAIM:DERIVED_FROM",
                "experience-1:EVIDENCE:CITES",
                "experience-1:REQUIREMENT:ADDRESSES",
            )
        assertThat(links.filter { it["source_type"] == "CLAIM" }.single()["source_revision"]).isEqualTo(1L)

        val result =
            jdbc.queryForMap(
                "select result ->> 'versionId' as version_id, result ->> 'pendingApproval' as pending, result ->> 'blocks' as blocks from jobs where id = ?",
                job,
            )
        assertThat(result["version_id"]).isEqualTo(version["id"].toString())
        assertThat(result["blocks"]).isEqualTo("2")
        assertThat(result["pending"]).isEqualTo("1")

        // Nothing accepted → the job fails without calling the model.
        jdbc.update(
            "update requirement_matches set user_decision = 'REJECTED' where application_id = ?",
            s.applicationId,
        )
        val second = enqueue(s.workspaceId, s.documentId)
        awaitStatus(second, "FAILED:NO_ACCEPTED_SOURCES")
        assertThat(fake.requests).hasSize(1)
    }

    private fun completed(json: String) =
        ModelOutcome.Completed(json, "claude-opus-5", ModelUsage(2_000, 500, 0, 0), false)

    private data class Seed(
        val workspaceId: UUID,
        val applicationId: UUID,
        val documentId: UUID,
        val requirement: UUID,
        val acceptedClaim: UUID,
        val rejectedClaim: UUID,
        val evidence: UUID,
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
        val text = "SaaS 제품 기획 경험"
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
        val requirement = UUID.randomUUID()
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at, sort_order) values (?, ?, 'REQUIRED', 'SaaS 제품 기획 경험', 1, 'USER', 'APPROVED', now(), 1)",
            requirement,
            snapshotId,
        )
        val applicationId = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title) values (?, ?, ?, 'ABC', 'PM')",
            applicationId,
            ws,
            snapshotId,
        )
        val documentId = UUID.randomUUID()
        jdbc.update(
            "insert into documents (id, workspace_id, application_id, type, title) values (?, ?, ?, 'COVER_LETTER', '자기소개서')",
            documentId,
            ws,
            applicationId,
        )

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
        val accepted = claim(ws, "SaaS 제품 기획을 주도해 온보딩을 재설계", "ACHIEVEMENT", achievement)
        val evidence = UUID.randomUUID()
        jdbc.update(
            "insert into evidence (id, workspace_id, type, title, source, uri, verification, captured_at) values (?, ?, 'URL', 'Q2 대시보드', 'USER_INPUT', 'https://a', 'USER_VERIFIED', now())",
            evidence,
            ws,
        )
        jdbc.update(
            "insert into claim_evidence (claim_id, evidence_id, relation, confidence) values (?, ?, 'SUPPORTS', 0.9)",
            accepted,
            evidence,
        )
        val rejected = claim(ws, "사내 봉사활동 참여", "CAREER_ENTRY", entry)
        match(applicationId, requirement, accepted, 1, "ACCEPTED")
        match(applicationId, requirement, rejected, 2, "REJECTED")
        return Seed(ws, applicationId, documentId, requirement, accepted, rejected, evidence)
    }

    private fun match(
        applicationId: UUID,
        requirementId: UUID,
        claimId: UUID,
        rank: Int,
        decision: String,
    ) = jdbc.update(
        """
        insert into requirement_matches (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, user_decision)
        values (?, ?, ?, ?, ?, 50, 'MEDIUM', '{}'::jsonb, 'SUPPORTED', ?)
        """.trimIndent(),
        UUID.randomUUID(),
        applicationId,
        requirementId,
        claimId,
        rank,
        decision,
    )

    private fun claim(
        ws: UUID,
        text: String,
        sourceType: String,
        sourceId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into claims (id, workspace_id, text, claim_type, sensitivity) values (?, ?, ?, 'FACT', 'INTERNAL')",
            id,
            ws,
            text,
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
        documentId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            workspaceId,
            DocumentGenerationJobHandler.TYPE,
            """{"documentId":"$documentId"}""",
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
