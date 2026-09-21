package com.proofu.api.document

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import com.proofu.api.identity.HeaderWorkspaceResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class DocumentApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var applicationId: UUID
    private lateinit var snapshotId: UUID
    private lateinit var requirementId: UUID
    private lateinit var claimId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
        val imported =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"SaaS 제품 기획 경험"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        snapshotId = UUID.fromString(mapper.readTree(imported).get("snapshotId").asString())
        applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        requirementId =
            support.create(
                workspaceId,
                "/api/v1/job-posting-snapshots/$snapshotId/requirements",
                """{"category":"REQUIRED","text":"SaaS 제품 기획 경험"}""",
            )
        val projectId =
            support.create(
                workspaceId,
                "/api/v1/projects",
                """{"name":"SaaS 온보딩","role":"PM","summary":"s"}""",
            )
        claimId =
            support.create(
                workspaceId,
                "/api/v1/claims",
                """{"text":"SaaS 제품 기획 주도","type":"FACT","sources":[{"type":"PROJECT","id":"$projectId"}]}""",
            )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `documents live under an application, drafting needs accepted sources, user versions obey the domain`() {
        val documentId =
            support.create(
                workspaceId,
                "/api/v1/applications/$applicationId/documents",
                """{"type":"COVER_LETTER","title":"자기소개서"}""",
            )
        val listed = support.getJson(workspaceId, "/api/v1/applications/$applicationId/documents")
        assertThat(support.titles(listed)).containsExactly("자기소개서")
        val all = support.getJson(workspaceId, "/api/v1/documents?q=abc")
        assertThat(support.titles(all)).containsExactly("자기소개서")
        assertThat((all["items"] as List<Map<*, *>>).single()["company"]).isEqualTo("ABC")
        assertThat((all["items"] as List<Map<*, *>>).single()["applicationStatus"]).isEqualTo("INTERESTED")
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/documents?type=RESUME"))).isEmpty()
        assertThat(support.titles(support.getJson(support.newWorkspace(), "/api/v1/documents"))).isEmpty()

        val empty = support.getJson(workspaceId, "/api/v1/documents/$documentId")
        assertThat(empty["latestVersion"]).isNull()
        assertThat(
            (empty["sections"] as List<Map<*, *>>).map {
                it["id"]
            },
        ).containsExactly("motivation", "experience", "contribution")

        // Nothing accepted yet → 422 with a domain code; nothing is queued.
        post(
            "/api/v1/documents/$documentId/generation-jobs",
            "{}",
        ).expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo("DOMAIN_RULE_VIOLATION")
        post(
            "/api/v1/documents/$documentId/generation-jobs",
            """{"templateVersion":"en-v9"}""",
        ).expectStatus().isEqualTo(422)

        jdbc.update(
            """
            insert into requirement_matches (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, user_decision)
            values (?, ?, ?, ?, 1, 60, 'MEDIUM', '{}'::jsonb, 'UNSUPPORTED', 'ACCEPTED')
            """.trimIndent(),
            UUID.randomUUID(),
            applicationId,
            requirementId,
            claimId,
        )
        val accepted =
            post("/api/v1/documents/$documentId/generation-jobs", "{}")
                .expectStatus()
                .isAccepted
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val jobId = mapper.readTree(accepted).get("jobId").asString()
        val payload =
            jdbc.queryForObject(
                "select (payload ->> 'documentId') || '|' || (payload ->> 'templateVersion') from jobs where id = ?::uuid",
                String::class.java,
                jobId,
            )
        assertThat(payload).isEqualTo("$documentId|ko-v1")
        // Same document while queued → same job.
        val again =
            post(
                "/api/v1/documents/$documentId/generation-jobs",
                "{}",
            ).expectStatus().isAccepted.expectBody(String::class.java).returnResult().responseBody
        assertThat(mapper.readTree(again).get("jobId").asString()).isEqualTo(jobId)

        // Simulate the worker's AI version.
        val aiVersion = UUID.randomUUID()
        jdbc.update(
            """
            insert into document_versions (id, document_id, content_json, template_version, prompt_version, model_ref, created_by)
            values (?, ?, ?::jsonb, 'ko-v1', 'draft-v1', 'claude-opus-5', 'AI')
            """.trimIndent(),
            aiVersion,
            documentId,
            """{"blocks":[
              {"blockId":"motivation-1","text":"동기","claimRefs":[],"evidenceRefs":[],"requirementRefs":["$requirementId"],"certainty":"UNSUPPORTED","warnings":[],"approvedByUser":false},
              {"blockId":"experience-1","text":"경험","claimRefs":["$claimId"],"evidenceRefs":[],"requirementRefs":["$requirementId"],"certainty":"INFERRED","warnings":[],"approvedByUser":false}
            ]}""",
        )
        jdbc.update(
            "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, 'experience-1', 'CLAIM', ?, 1, 'DERIVED_FROM')",
            aiVersion,
            claimId,
        )
        jdbc.update(
            "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, 'experience-1', 'REQUIREMENT', ?, 1, 'ADDRESSES')",
            aiVersion,
            requirementId,
        )

        val detail = support.getJson(workspaceId, "/api/v1/documents/$documentId")
        assertThat(detail["latestVersionId"]).isEqualTo(aiVersion.toString())
        assertThat(detail["latestVersionCreatedBy"]).isEqualTo("AI")
        assertThat(detail["pendingApprovalCount"]).isEqualTo(2)
        val latest = detail["latestVersion"] as Map<String, Any?>
        assertThat((latest["provenance"] as List<*>)).hasSize(2)

        // A stale parent is a conflict.
        post("/api/v1/documents/$documentId/versions", """{"parentVersionId":null,"blocks":[]}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")

        // Adding a reference the parent block did not have is a domain violation.
        post(
            "/api/v1/documents/$documentId/versions",
            """{"parentVersionId":"$aiVersion","blocks":[{"blockId":"motivation-1","text":"동기","claimRefs":["$claimId"],"certainty":"SUPPORTED"}]}""",
        ).expectStatus().isEqualTo(422)

        // Approve one block, edit the other's text, try to raise its certainty, and drop nothing.
        val userVersion =
            post(
                "/api/v1/documents/$documentId/versions",
                """{"parentVersionId":"$aiVersion","label":"1차 검토","blocks":[
                  {"blockId":"motivation-1","text":"동기","certainty":"UNSUPPORTED","approvedByUser":true},
                  {"blockId":"experience-1","text":"경험 (수정)","claimRefs":["$claimId"],"requirementRefs":["$requirementId"],"certainty":"SUPPORTED"}
                ]}""",
            ).expectStatus().isCreated.expectBody(String::class.java).returnResult().responseBody
        val saved = mapper.readTree(userVersion)
        assertThat(saved.get("createdBy").asString()).isEqualTo("USER")
        assertThat(saved.get("parentId").asString()).isEqualTo(aiVersion.toString())
        assertThat(saved.get("label").asString()).isEqualTo("1차 검토")
        val blocks = saved.get("blocks")
        assertThat(blocks.get(0).get("approvedByUser").asBoolean()).isTrue()
        assertThat(blocks.get(1).get("text").asString()).isEqualTo("경험 (수정)")
        assertThat(blocks.get(1).get("certainty").asString()).isEqualTo("INFERRED") // cannot be raised
        assertThat(saved.get("provenance")).hasSize(2) // carried over with the retained references

        val after = support.getJson(workspaceId, "/api/v1/documents/$documentId")
        assertThat(after["pendingApprovalCount"]).isEqualTo(1)
        assertThat(after["version"]).isEqualTo(2)
        val versions = support.getJson(workspaceId, "/api/v1/documents/$documentId/versions")
        assertThat((versions["items"] as List<Map<*, *>>).map { it["createdBy"] }).containsExactly("USER", "AI")

        // Other workspaces see nothing.
        val other = support.newWorkspace()
        client
            .get()
            .uri("/api/v1/documents/$documentId")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
        client
            .get()
            .uri("/api/v1/document-versions/$aiVersion")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }

    private fun post(
        path: String,
        json: String,
    ) = client
        .post()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()
}
