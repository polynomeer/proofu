package com.proofu.api.evidence

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
class EvidenceApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
    }

    private fun post(json: String) =
        client
            .post()
            .uri("/api/v1/evidence")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    @Test
    fun `url and note evidence are created with their type-specific location`() {
        val url =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"URL","title":"Retro","uri":"https://example.com/retro"}""",
            )
        val note =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"NOTE","title":"Memo","body":"Shipped 2024-03"}""",
            )

        val u = support.getJson(workspaceId, "/api/v1/evidence/$url")
        assertThat(u["uri"]).isEqualTo("https://example.com/retro")
        assertThat(u["verification"]).isEqualTo("UNVERIFIED")
        assertThat(u["source"]).isEqualTo("USER_INPUT")
        assertThat(u["linkedClaimCount"]).isEqualTo(0)
        assertThat(support.getJson(workspaceId, "/api/v1/evidence/$note")["body"]).isEqualTo("Shipped 2024-03")
    }

    @Test
    fun `type-specific location rules and the file exclusion are domain violations`() {
        post("""{"type":"NOTE","title":"Memo"}""").expectStatus().isEqualTo(422)
        post("""{"type":"URL","title":"x","uri":"example.com"}""").expectStatus().isEqualTo(422)
        post("""{"type":"FILE","title":"x"}""").expectStatus().isEqualTo(422)
    }

    @Test
    fun `users can verify but not externally verify`() {
        val id =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"URL","title":"Retro","uri":"https://example.com"}""",
            )
        val patch = """{"type":"URL","title":"Retro","uri":"https://example.com","verification":"%s","revision":1}"""
        client
            .patch()
            .uri("/api/v1/evidence/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(patch.format("EXTERNALLY_VERIFIED"))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
        client
            .patch()
            .uri("/api/v1/evidence/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(patch.format("USER_VERIFIED"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.verification")
            .isEqualTo("USER_VERIFIED")
            .jsonPath("$.revision")
            .isEqualTo(2)
    }

    @Test
    fun `list filters by type and verification and searches title and body`() {
        support.create(
            workspaceId,
            "/api/v1/evidence",
            """{"type":"URL","title":"Dashboard capture","uri":"https://a","capturedAt":"2024-01-01T00:00:00Z"}""",
        )
        support.create(
            workspaceId,
            "/api/v1/evidence",
            """{"type":"NOTE","title":"Memo","body":"kubernetes migration notes","capturedAt":"2024-02-01T00:00:00Z","verification":"USER_VERIFIED"}""",
        )

        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/evidence")),
        ).containsExactly("Memo", "Dashboard capture")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/evidence?type=URL")),
        ).containsExactly("Dashboard capture")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/evidence?verification=USER_VERIFIED")),
        ).containsExactly("Memo")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/evidence?q=kubernetes")),
        ).containsExactly("Memo")
        val first = support.getJson(workspaceId, "/api/v1/evidence?limit=1")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/evidence?limit=1&cursor=${first["nextCursor"]}")),
        ).containsExactly("Dashboard capture")
    }

    @Test
    fun `evidence of another workspace is not found`() {
        val id = support.create(workspaceId, "/api/v1/evidence", """{"type":"URL","title":"x","uri":"https://a"}""")
        client
            .get()
            .uri("/api/v1/evidence/$id")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
