package com.proofu.api.matching

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
class MatchApiTest {
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
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"SaaS 제품 기획 경험\nKotlin"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        snapshotId = UUID.fromString(mapper.readTree(imported).get("snapshotId").asString())
        applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `report lists approved requirements with candidates, verdicts and gaps, and decisions round-trip`() {
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
        val evidenceId =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"URL","title":"대시보드","uri":"https://a","verification":"USER_VERIFIED"}""",
            )
        client
            .post()
            .uri("/api/v1/claims/$claimId/evidence")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"evidenceId":"$evidenceId","relation":"SUPPORTS","confidence":0.9}""")
            .exchange()
            .expectStatus()
            .isCreated

        // Before any run: groups exist, no candidates, REQUIRED items are UNMET gaps.
        val before = support.getJson(workspaceId, "/api/v1/applications/$applicationId/matches")
        assertThat(before["hasRun"]).isEqualTo(false)
        assertThat((before["gaps"] as List<*>)).containsExactlyInAnyOrder(reqSaas.toString(), reqKotlin.toString())

        // Simulate a worker run: one stored match for the SaaS requirement.
        val matchId = UUID.randomUUID()
        jdbc.update(
            """
            insert into requirement_matches (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, reason)
            values (?, ?, ?, ?, 1, 82, 'HIGH', '{"requirementCoverage":1,"evidenceStrength":0.5,"recency":1,"complexity":0.6,"impact":0.4,"semanticSimilarity":0}', 'SUPPORTED', '온보딩 재설계 사실이 뒷받침합니다.')
            """.trimIndent(),
            matchId,
            applicationId,
            reqSaas,
            claimId,
        )
        val job = UUID.randomUUID()
        jdbc.update(
            "insert into jobs (id, workspace_id, type, status, payload, finished_at) values (?, ?, 'application.match', 'SUCCEEDED', ?::jsonb, now())",
            job,
            workspaceId,
            """{"applicationId":"$applicationId"}""",
        )

        val report = support.getJson(workspaceId, "/api/v1/applications/$applicationId/matches")
        assertThat(report["hasRun"]).isEqualTo(true)
        val groups = report["groups"] as List<Map<*, *>>
        val saas = groups.first { (it["requirement"] as Map<*, *>)["id"] == reqSaas.toString() }
        assertThat(saas["assessment"]).isEqualTo("MET")
        val candidate = (saas["candidates"] as List<Map<*, *>>).single()
        assertThat(candidate["claimText"]).isEqualTo("SaaS 제품 기획 주도")
        assertThat(candidate["claimStatus"]).isEqualTo("SUPPORTED")
        assertThat((candidate["evidence"] as List<Map<*, *>>).single()["title"]).isEqualTo("대시보드")
        assertThat(candidate["reason"]).isEqualTo("온보딩 재설계 사실이 뒷받침합니다.")
        assertThat(groups.first()["requirement"]).isEqualTo(saas["requirement"]) // REQUIRED with best score first
        assertThat(report["gaps"] as List<*>).containsExactly(reqKotlin.toString())

        client
            .put()
            .uri("/api/v1/requirement-matches/$matchId/decision")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"decision":"ACCEPTED"}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userDecision")
            .isEqualTo("ACCEPTED")
        client
            .put()
            .uri("/api/v1/requirement-matches/$matchId/decision")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"decision":"REJECTED"}""")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `match jobs are accepted once per pending application`() {
        val first =
            mapper
                .readTree(
                    client
                        .post()
                        .uri(
                            "/api/v1/applications/$applicationId/match-jobs",
                        ).header(
                            HeaderWorkspaceResolver.HEADER,
                            workspaceId.toString(),
                        ).exchange()
                        .expectStatus()
                        .isAccepted
                        .expectBody(String::class.java)
                        .returnResult()
                        .responseBody,
                ).get("jobId")
                .asString()
        val second =
            mapper
                .readTree(
                    client
                        .post()
                        .uri(
                            "/api/v1/applications/$applicationId/match-jobs",
                        ).header(
                            HeaderWorkspaceResolver.HEADER,
                            workspaceId.toString(),
                        ).exchange()
                        .expectBody(String::class.java)
                        .returnResult()
                        .responseBody,
                ).get("jobId")
                .asString()
        assertThat(second).isEqualTo(first)
        assertThat(support.getJson(workspaceId, "/api/v1/jobs/$first")["type"]).isEqualTo("application.match")
    }
}
