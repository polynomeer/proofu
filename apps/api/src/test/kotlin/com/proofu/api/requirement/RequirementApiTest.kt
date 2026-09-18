package com.proofu.api.requirement

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
class RequirementApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
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
                .body(
                    """{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"Kotlin 5+ years\nLeads a team"}""",
                ).exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        snapshotId = UUID.fromString(mapper.readTree(imported).get("snapshotId").asString())
    }

    private fun path() = "/api/v1/job-posting-snapshots/$snapshotId/requirements"

    private fun post(
        json: String,
        workspace: UUID = workspaceId,
    ) = client
        .post()
        .uri(path())
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    private fun patch(
        id: UUID,
        json: String,
    ) = client
        .patch()
        .uri("/api/v1/requirements/$id")
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `manual requirements are approved as written and resolve their excerpt`() {
        val id =
            support.create(
                workspaceId,
                path(),
                """{"category":"REQUIRED","text":"Kotlin 5년 이상","sourceSpan":{"start":0,"end":15}}""",
            )
        val r = support.getJson(workspaceId, "/api/v1/requirements/$id")
        assertThat(r["status"]).isEqualTo("APPROVED")
        assertThat(r["origin"]).isEqualTo("USER")
        assertThat(r["confidence"]).isEqualTo(1.0)
        assertThat(r["excerpt"]).isEqualTo("Kotlin 5+ years")
        assertThat(r["approvedAt"]).isNotNull
    }

    @Test
    fun `a span outside the text and a foreign snapshot are rejected`() {
        post("""{"category":"REQUIRED","text":"x","sourceSpan":{"start":0,"end":999}}""").expectStatus().isEqualTo(422)
        post("""{"category":"REQUIRED","text":"x"}""", support.newWorkspace()).expectStatus().isNotFound
    }

    @Test
    fun `review can exclude, re-approve, reword and never set DRAFT`() {
        val id = support.create(workspaceId, path(), """{"category":"SKILL","text":"Kotlin"}""")

        patch(id, """{"status":"REJECTED","version":1}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REJECTED")
            .jsonPath("$.approvedAt")
            .doesNotExist()
        patch(id, """{"status":"APPROVED","category":"REQUIRED","text":"Kotlin (필수)","version":2}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("APPROVED")
            .jsonPath("$.category")
            .isEqualTo("REQUIRED")
            .jsonPath("$.text")
            .isEqualTo("Kotlin (필수)")
            .jsonPath("$.version")
            .isEqualTo(3)
        patch(id, """{"status":"DRAFT","version":3}""").expectStatus().isBadRequest
        patch(id, """{"text":"x","version":1}""").expectStatus().isEqualTo(409)
    }

    @Test
    fun `list keeps insertion order and delete hides the requirement`() {
        val a = support.create(workspaceId, path(), """{"category":"REQUIRED","text":"first"}""")
        support.create(workspaceId, path(), """{"category":"PREFERRED","text":"second"}""")
        assertThat(support.titles(support.getJson(workspaceId, path()), "text")).containsExactly("first", "second")

        client
            .delete()
            .uri("/api/v1/requirements/$a")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.titles(support.getJson(workspaceId, path()), "text")).containsExactly("second")
        assertThat(
            jdbc.queryForObject("select deleted_at is not null from requirements where id = ?", Boolean::class.java, a),
        ).isTrue()
    }
}
