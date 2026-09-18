package com.proofu.api.project

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
class ProjectApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var entryId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
        entryId =
            support.create(
                workspaceId,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2020-01-01"}""",
            )
    }

    @Test
    fun `create attaches to a career entry and stores links as jsonb`() {
        val id =
            support.create(
                workspaceId,
                "/api/v1/projects",
                """{"careerEntryId":"$entryId","name":"Checkout","role":"Lead","summary":"Rebuilt checkout",
                    "startDate":"2021-01-01","teamSize":4,
                    "links":[{"label":"Repo","url":"https://example.com/repo"}]}""",
            )

        val project = support.getJson(workspaceId, "/api/v1/projects/$id")
        assertThat(project["careerEntryId"]).isEqualTo(entryId.toString())
        assertThat(project["links"]).isEqualTo(listOf(mapOf("label" to "Repo", "url" to "https://example.com/repo")))
        assertThat(project["revision"]).isEqualTo(1)
        assertThat(jdbc.queryForObject("select links::text from projects where id = ?", String::class.java, id))
            .contains("\"url\"")
    }

    @Test
    fun `attaching to an entry of another workspace is not found`() {
        val other = support.newWorkspace()
        client
            .post()
            .uri("/api/v1/projects")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"careerEntryId":"$entryId","name":"X","role":"Y","summary":"Z"}""")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_FOUND")
    }

    @Test
    fun `invalid link url is a domain rule violation`() {
        client
            .post()
            .uri("/api/v1/projects")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"X","role":"Y","summary":"Z","links":[{"label":"Repo","url":"example.com"}]}""")
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
    }

    @Test
    fun `list filters by entry, sorts undated projects first and pages`() {
        support.create(
            workspaceId,
            "/api/v1/projects",
            """{"careerEntryId":"$entryId","name":"Old","role":"r","summary":"s","startDate":"2019-01-01"}""",
        )
        support.create(
            workspaceId,
            "/api/v1/projects",
            """{"careerEntryId":"$entryId","name":"Recent","role":"r","summary":"s","startDate":"2023-01-01"}""",
        )
        support.create(workspaceId, "/api/v1/projects", """{"name":"Undated","role":"r","summary":"s"}""")

        val all = support.getJson(workspaceId, "/api/v1/projects?limit=2")
        assertThat(support.titles(all, "name")).containsExactly("Undated", "Recent")
        val rest = support.getJson(workspaceId, "/api/v1/projects?limit=2&cursor=${all["nextCursor"]}")
        assertThat(support.titles(rest, "name")).containsExactly("Old")
        assertThat(rest["nextCursor"]).isNull()

        val byEntry = support.getJson(workspaceId, "/api/v1/projects?careerEntryId=$entryId")
        assertThat(support.titles(byEntry, "name")).containsExactly("Recent", "Old")
    }

    @Test
    fun `update checks revision and delete cascades to achievements`() {
        val id = support.create(workspaceId, "/api/v1/projects", """{"name":"P","role":"r","summary":"s"}""")
        val achievementId =
            support.create(
                workspaceId,
                "/api/v1/projects/$id/achievements",
                """{"action":"Cut build time","outcome":"CI 2x faster","confidence":0.8}""",
            )

        client
            .patch()
            .uri("/api/v1/projects/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"P2","role":"r","summary":"s","revision":7}""")
            .exchange()
            .expectStatus()
            .isEqualTo(409)
        client
            .patch()
            .uri("/api/v1/projects/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"P2","role":"r","summary":"s","revision":1}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.revision")
            .isEqualTo(2)

        client
            .delete()
            .uri("/api/v1/projects/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        client
            .get()
            .uri("/api/v1/achievements/$achievementId")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNotFound
        assertThat(
            jdbc.queryForObject(
                "select deleted_at is not null from achievements where id = ?",
                Boolean::class.java,
                achievementId,
            ),
        ).isTrue()
    }
}
