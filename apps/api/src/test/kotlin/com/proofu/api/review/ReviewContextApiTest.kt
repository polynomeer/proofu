package com.proofu.api.review

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
class ReviewContextApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `review context reports the submitted document's facts against approved requirements`() {
        val support = ApiTestSupport(client, jdbc, mapper)
        val workspaceId = support.newWorkspace()
        val imported =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"SaaS 제품 기획 경험\nKotlin"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val snapshotId = mapper.readTree(imported).get("snapshotId").asString()
        val applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        val reqSaas =
            support.create(
                workspaceId,
                "/api/v1/job-posting-snapshots/$snapshotId/requirements",
                """{"category":"REQUIRED","text":"SaaS 제품 기획 경험"}""",
            )
        val reqKotlin =
            support.create(
                workspaceId,
                "/api/v1/job-posting-snapshots/$snapshotId/requirements",
                """{"category":"REQUIRED","text":"Kotlin"}""",
            )

        // Before any submission: posting facts and UNMET verdicts only.
        val before = support.getJson(workspaceId, "/api/v1/applications/$applicationId/review-context")
        assertThat((before["postingSnapshot"] as Map<*, *>)["approvedRequirementCount"]).isEqualTo(2)
        assertThat(before["submission"]).isNull()
        assertThat(before["document"]).isNull()
        val reqsBefore = before["requirements"] as List<Map<*, *>>
        assertThat(reqsBefore.map { it["assessment"] }).containsExactly("UNMET", "UNMET")
        assertThat(reqsBefore.map { it["addressedBySubmission"] }).containsExactly(false, false)

        val documentId =
            support.create(
                workspaceId,
                "/api/v1/applications/$applicationId/documents",
                """{"type":"RESUME","title":"이력서"}""",
            )
        val projectId =
            support.create(
                workspaceId,
                "/api/v1/projects",
                """{"name":"SaaS 온보딩","role":"PM","summary":"s"}""",
            )
        val claimId =
            support.create(
                workspaceId,
                "/api/v1/claims",
                """{"text":"SaaS 제품 기획 주도","type":"FACT","sources":[{"type":"PROJECT","id":"$projectId"}]}""",
            )
        val version = UUID.randomUUID()
        jdbc.update(
            """
            insert into document_versions (id, document_id, content_json, template_version, created_by)
            values (?, ?, ?::jsonb, 'ko-v1', 'USER')
            """.trimIndent(),
            version,
            documentId,
            """{"blocks":[
              {"blockId":"summary-1","text":"요약","claimRefs":[],"evidenceRefs":[],"requirementRefs":[],"certainty":"UNSUPPORTED","warnings":[],"approvedByUser":true},
              {"blockId":"career-1","text":"경력","claimRefs":["$claimId"],"evidenceRefs":[],"requirementRefs":["$reqSaas"],"certainty":"INFERRED","warnings":[],"approvedByUser":true}
            ]}""",
        )
        client
            .post()
            .uri("/api/v1/applications/$applicationId/submissions")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"documentVersionId":"$version"}""")
            .exchange()
            .expectStatus()
            .isCreated

        val after = support.getJson(workspaceId, "/api/v1/applications/$applicationId/review-context")
        assertThat(after["status"]).isEqualTo("SUBMITTED")
        assertThat(after["submissionCount"]).isEqualTo(1)
        assertThat((after["submission"] as Map<*, *>)["documentTitle"]).isEqualTo("이력서")
        val document = after["document"] as Map<*, *>
        assertThat(document["totalBlocks"]).isEqualTo(2)
        assertThat(document["blocksWithClaims"]).isEqualTo(1)
        assertThat(document["supportedBlocks"]).isEqualTo(0)
        assertThat(document["approvedWithoutEvidence"]).isEqualTo(2)
        assertThat(document["evidenceLinkRate"]).isEqualTo(50)
        assertThat(document["requirementCoverage"]).isEqualTo(50)
        val ats = after["ats"] as Map<String, Any?>
        assertThat(ats["documentVersionId"]).isEqualTo(version.toString())
        val findings = (ats["findings"] as List<Map<*, *>>).associateBy { it["code"] }
        assertThat(findings.getValue("KEYWORDS")["details"]).isEqualTo(listOf("SaaS 제품 기획 경험", "Kotlin"))
        assertThat(findings.getValue("CONTACT")["severity"]).isEqualTo("WARN")
        assertThat(findings.getValue("FILE_FORMAT")["severity"]).isEqualTo("INFO")
        assertThat(before["ats"]).isNull()

        // The same report is served on its own endpoint.
        val direct = support.getJson(workspaceId, "/api/v1/document-versions/$version/ats-check")
        assertThat(direct["warnings"]).isEqualTo(ats["warnings"])
        val reqs = after["requirements"] as List<Map<*, *>>
        assertThat(reqs.first { it["id"] == reqSaas.toString() }["addressedBySubmission"]).isEqualTo(true)
        assertThat(reqs.first { it["id"] == reqKotlin.toString() }["addressedBySubmission"]).isEqualTo(false)

        client
            .get()
            .uri("/api/v1/applications/$applicationId/review-context")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
