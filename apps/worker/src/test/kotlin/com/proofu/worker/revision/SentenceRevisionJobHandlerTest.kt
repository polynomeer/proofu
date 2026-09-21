package com.proofu.worker.revision

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
@Import(TestcontainersConfiguration::class, SentenceRevisionJobHandlerTest.ScriptedModel::class)
@ActiveProfiles("test")
class SentenceRevisionJobHandlerTest {
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
    fun `proposes a rewrite from the block and its claims, and refuses one that changes a number`() {
        val fake = model as FakeModelClient
        val s = seed()
        fake.enqueue(
            ModelOutcome.Completed(
                """{"revised":"온보딩을 5→2단계로 줄여 7일 활성화율을 40% 개선했습니다.","changes":["군더더기 제거"]}""",
                "claude-opus-5",
                ModelUsage(300, 80, 0, 0),
                false,
            ),
        )
        val first = enqueue(s, "SHORTEN")
        awaitStatus(first, "SUCCEEDED")
        val result =
            jdbc.queryForMap(
                "select result ->> 'revised' as revised, result ->> 'original' as original, result -> 'changes' ->> 0 as change from jobs where id = ?",
                first,
            )
        assertThat(result["revised"]).isEqualTo("온보딩을 5→2단계로 줄여 7일 활성화율을 40% 개선했습니다.")
        assertThat(result["original"]).isEqualTo(s.text)
        assertThat(result["change"]).isEqualTo("군더더기 제거")
        // The model only saw the block and the cited claim, never other workspace data.
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).containsExactly("block:experience-1", "fact:0")
        assertThat(
            fake.requests
                .single()
                .documents[1]
                .text,
        ).isEqualTo("가입 후 7일 활성화율 상승")

        fake.enqueue(
            ModelOutcome.Completed(
                """{"revised":"활성화율을 45% 개선했습니다.","changes":[]}""",
                "claude-opus-5",
                ModelUsage(300, 80, 0, 0),
                false,
            ),
        )
        val second = enqueue(s, "CLARIFY")
        awaitStatus(second, "SUCCEEDED")
        val refused =
            jdbc.queryForMap(
                "select result ->> 'revised' as revised, result -> 'rejected' ->> 0 as why from jobs where id = ?",
                second,
            )
        assertThat(refused["revised"]).isNull()
        assertThat(refused["why"].toString()).contains("45")
        // Nothing was written to the version.
        assertThat(
            jdbc.queryForObject(
                "select content_json::text from document_versions where id = ?",
                String::class.java,
                s.versionId,
            ),
        ).contains(s.text)
            .doesNotContain("45%")
    }

    private data class Seed(
        val workspaceId: UUID,
        val documentId: UUID,
        val versionId: UUID,
        val text: String,
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
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            postingId,
            ws,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', 't', ?, now())",
            snapshotId,
            postingId,
            ContentHash.of("t"),
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
        val claimId = UUID.randomUUID()
        jdbc.update(
            "insert into claims (id, workspace_id, text, claim_type, sensitivity) values (?, ?, '가입 후 7일 활성화율 상승', 'FACT', 'INTERNAL')",
            claimId,
            ws,
        )
        val text = "온보딩 플로우를 5단계에서 2단계로 축소해서 활성화율을 40% 정도 개선을 했습니다."
        val versionId = UUID.randomUUID()
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) values (?, ?, ?::jsonb, 'ko-v1', 'AI')",
            versionId,
            documentId,
            """{"blocks":[{"blockId":"experience-1","text":"$text","claimRefs":["$claimId"],"evidenceRefs":[],"requirementRefs":[],"certainty":"SUPPORTED","warnings":[],"approvedByUser":false}]}""",
        )
        return Seed(ws, documentId, versionId, text)
    }

    private fun enqueue(
        s: Seed,
        mode: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            s.workspaceId,
            SentenceRevisionJobHandler.TYPE,
            """{"documentId":"${s.documentId}","versionId":"${s.versionId}","blockId":"experience-1","mode":"$mode"}""",
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
