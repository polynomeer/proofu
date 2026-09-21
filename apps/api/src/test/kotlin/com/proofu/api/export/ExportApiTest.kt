package com.proofu.api.export

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import com.proofu.api.identity.HeaderWorkspaceResolver
import org.assertj.core.api.Assertions.assertThat
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
class ExportApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `exports are gated, deduplicated, listed and downloadable only when READY`() {
        val support = ApiTestSupport(client, jdbc, mapper)
        val workspaceId = support.newWorkspace()
        val imported =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"text"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val snapshotId = mapper.readTree(imported).get("snapshotId").asString()
        val applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        val documentId =
            support.create(
                workspaceId,
                "/api/v1/applications/$applicationId/documents",
                """{"type":"RESUME","title":"이력서/2026"}""",
            )
        val pendingVersion = UUID.randomUUID()
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) values (?, ?, ?::jsonb, 'ko-v1', 'AI')",
            pendingVersion,
            documentId,
            """{"blocks":[{"blockId":"summary-1","text":"요약","claimRefs":[],"evidenceRefs":[],"requirementRefs":[],"certainty":"UNSUPPORTED","warnings":[],"approvedByUser":false}]}""",
        )

        fun start(
            version: UUID,
            format: String,
        ) = client
            .post()
            .uri("/api/v1/document-versions/$version/exports")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"format":"$format"}""")
            .exchange()

        start(
            pendingVersion,
            "DOCX",
        ).expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_CLAIM_IN_EXPORT")

        val approved =
            support.create(
                workspaceId,
                "/api/v1/documents/$documentId/versions",
                """{"parentVersionId":"$pendingVersion","blocks":[{"blockId":"summary-1","text":"요약","certainty":"UNSUPPORTED","approvedByUser":true}]}""",
            )

        val first =
            mapper.readTree(
                start(approved, "DOCX")
                    .expectStatus()
                    .isAccepted
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody,
            )
        val again =
            mapper.readTree(
                start(approved, "DOCX")
                    .expectStatus()
                    .isAccepted
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody,
            )
        assertThat(again.get("jobId")).isEqualTo(first.get("jobId"))
        assertThat(again.get("exportId")).isEqualTo(first.get("exportId"))
        val markdown =
            mapper.readTree(
                start(
                    approved,
                    "MARKDOWN",
                ).expectStatus().isAccepted.expectBody(String::class.java).returnResult().responseBody,
            )
        assertThat(markdown.get("exportId")).isNotEqualTo(first.get("exportId"))

        val exportId = first.get("exportId").asString()
        val export = support.getJson(workspaceId, "/api/v1/exports/$exportId")
        assertThat(export["status"]).isEqualTo("REQUESTED")
        assertThat(export["fileName"]).isEqualTo("이력서 2026.docx")
        assertThat(export["jobId"]).isEqualTo(first.get("jobId").asString())
        val list = support.getJson(workspaceId, "/api/v1/document-versions/$approved/exports")
        assertThat(list["items"] as List<*>).hasSize(2)

        client
            .get()
            .uri("/api/v1/exports/$exportId/file")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("EXPORT_NOT_READY")

        // Simulate the worker: file stored, export READY → download carries the bytes and a UTF-8 name.
        jdbc.update(
            "insert into export_files (object_key, content) values (?, ?)",
            "pg:$exportId",
            "hello".toByteArray(),
        )
        jdbc.update(
            "update exports set status = 'READY', object_key = ?, sha256 = repeat('a', 64), size_bytes = 5, mime_type = 'text/plain' where id = ?::uuid",
            "pg:$exportId",
            exportId,
        )
        val body =
            client
                .get()
                .uri("/api/v1/exports/$exportId/file")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .exchange()
                .expectStatus()
                .isOk
                .expectHeader()
                .valueMatches("Content-Disposition", ".*filename\\*=UTF-8''.*2026\\.docx.*")
                .expectBody(ByteArray::class.java)
                .returnResult()
                .responseBody
        assertThat(String(body!!)).isEqualTo("hello")

        client
            .get()
            .uri("/api/v1/exports/$exportId")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
