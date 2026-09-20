package com.proofu.api.submission

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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class SubmissionApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var applicationId: UUID
    private lateinit var documentId: UUID
    private lateinit var aiVersion: UUID

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
        val snapshotId = UUID.fromString(mapper.readTree(imported).get("snapshotId").asString())
        applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        documentId =
            support.create(
                workspaceId,
                "/api/v1/applications/$applicationId/documents",
                """{"type":"RESUME","title":"이력서"}""",
            )
        aiVersion = UUID.randomUUID()
        jdbc.update(
            """
            insert into document_versions (id, document_id, content_json, template_version, prompt_version, model_ref, created_by)
            values (?, ?, ?::jsonb, 'ko-v1', 'draft-v1', 'claude-opus-5', 'AI')
            """.trimIndent(),
            aiVersion,
            documentId,
            """{"blocks":[
              {"blockId":"summary-1","text":"요약","claimRefs":[],"evidenceRefs":[],"requirementRefs":[],"certainty":"UNSUPPORTED","warnings":[],"approvedByUser":false}
            ]}""",
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `submitting freezes an approved version, moves the application and never changes afterwards`() {
        // Unapproved block → refused with the export code, no snapshot, no transition.
        post("/api/v1/applications/$applicationId/submissions", """{"documentVersionId":"$aiVersion"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("UNSUPPORTED_CLAIM_IN_EXPORT")
        assertThat(
            support.getJson(workspaceId, "/api/v1/applications/$applicationId")["status"],
        ).isEqualTo("INTERESTED")

        val approved =
            support.create(
                workspaceId,
                "/api/v1/documents/$documentId/versions",
                """{"parentVersionId":"$aiVersion","label":"제출본","blocks":[{"blockId":"summary-1","text":"요약","certainty":"UNSUPPORTED","approvedByUser":true}]}""",
            )

        // Future dates are refused.
        post(
            "/api/v1/applications/$applicationId/submissions",
            """{"documentVersionId":"$approved","submittedAt":"2099-01-01T00:00:00Z"}""",
        ).expectStatus().isEqualTo(422)

        val yesterday = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS)
        val created =
            post(
                "/api/v1/applications/$applicationId/submissions",
                """{"documentVersionId":"$approved","submittedAt":"$yesterday","note":"포털 제출"}""",
            ).expectStatus().isCreated.expectBody(String::class.java).returnResult().responseBody
        val submission = mapper.readTree(created)
        assertThat(submission.get("documentTitle").asString()).isEqualTo("이력서")
        assertThat(submission.get("versionLabel").asString()).isEqualTo("제출본")
        assertThat(submission.get("versionCreatedBy").asString()).isEqualTo("USER")
        assertThat(submission.get("hash").asString()).hasSize(64)
        assertThat(Instant.parse(submission.get("submittedAt").asString())).isEqualTo(yesterday)

        val application = support.getJson(workspaceId, "/api/v1/applications/$applicationId")
        assertThat(application["status"]).isEqualTo("SUBMITTED")
        val events = application["events"] as List<Map<*, *>>
        assertThat(events.map { it["to"] }).containsExactly("INTERESTED", "PREPARING", "SUBMITTED")
        assertThat(events.last()["note"]).isEqualTo("포털 제출")

        // A correction while SUBMITTED adds a snapshot without another transition.
        post("/api/v1/applications/$applicationId/submissions", """{"documentVersionId":"$approved"}""")
            .expectStatus()
            .isCreated
        val list = support.getJson(workspaceId, "/api/v1/applications/$applicationId/submissions")
        assertThat(list["items"] as List<*>).hasSize(2)
        assertThat(
            (support.getJson(workspaceId, "/api/v1/applications/$applicationId")["events"] as List<*>),
        ).hasSize(3)

        val id = submission.get("id").asString()
        val detail = support.getJson(workspaceId, "/api/v1/submission-snapshots/$id")
        assertThat(detail["company"]).isEqualTo("ABC")
        val version = detail["version"] as Map<String, Any?>
        assertThat((version["blocks"] as List<Map<*, *>>).single()["approvedByUser"]).isEqualTo(true)

        // The row is immutable at the database level too.
        assertThat(
            runCatching {
                jdbc.update(
                    "update submission_snapshots set hash = repeat('0', 64) where id = ?::uuid",
                    id,
                )
            }.isFailure,
        ).isTrue()
        assertThat(
            runCatching { jdbc.update("delete from submission_snapshots where id = ?::uuid", id) }.isFailure,
        ).isTrue()

        // Later statuses refuse new submissions.
        client
            .post()
            .uri("/api/v1/applications/$applicationId/transitions")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"to":"DOCUMENT_REJECTED","version":${application["version"]}}""")
            .exchange()
            .expectStatus()
            .isOk
        post("/api/v1/applications/$applicationId/submissions", """{"documentVersionId":"$approved"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")

        // Another workspace sees nothing.
        client
            .get()
            .uri("/api/v1/submission-snapshots/$id")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `a version of another application's document is refused`() {
        val other =
            support.create(
                workspaceId,
                "/api/v1/applications",
                """{"snapshotId":"${snapshotOf(applicationId)}"}""",
            )
        val approved =
            support.create(
                workspaceId,
                "/api/v1/documents/$documentId/versions",
                """{"parentVersionId":"$aiVersion","blocks":[{"blockId":"summary-1","text":"요약","certainty":"UNSUPPORTED","approvedByUser":true}]}""",
            )
        post("/api/v1/applications/$other/submissions", """{"documentVersionId":"$approved"}""")
            .expectStatus()
            .isEqualTo(422)
    }

    private fun snapshotOf(applicationId: UUID): String =
        support.getJson(workspaceId, "/api/v1/applications/$applicationId")["snapshotId"] as String

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
