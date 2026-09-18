package com.proofu.api.achievement

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
class AchievementApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var projectId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
        projectId = support.create(workspaceId, "/api/v1/projects", """{"name":"P","role":"r","summary":"s"}""")
    }

    private fun post(
        workspace: UUID,
        json: String,
    ) = client
        .post()
        .uri("/api/v1/projects/$projectId/achievements")
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `metric with unit round-trips and confidence is stored with two decimals`() {
        val id =
            support.create(
                workspaceId,
                "/api/v1/projects/$projectId/achievements",
                """{"action":"Introduced caching","outcome":"p95 latency down","metricValue":40,"metricUnit":"%",
                    "baseline":"1200ms","timeframe":"Q3 2024","confidence":0.756}""",
            )
        val a = support.getJson(workspaceId, "/api/v1/achievements/$id")
        assertThat((a["metricValue"] as Number).toDouble()).isEqualTo(40.0)
        assertThat(a["metricUnit"]).isEqualTo("%")
        assertThat(a["confidence"]).isEqualTo(0.76)
        assertThat(a["projectId"]).isEqualTo(projectId.toString())
    }

    @Test
    fun `a metric value without a unit is rejected by the domain`() {
        post(workspaceId, """{"action":"a","outcome":"o","metricValue":40,"confidence":0.5}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
    }

    @Test
    fun `confidence outside the unit interval is a validation error`() {
        post(workspaceId, """{"action":"a","outcome":"o","confidence":1.5}""")
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.fieldErrors[0].field")
            .isEqualTo("confidence")
    }

    @Test
    fun `list is scoped to the project and ordered by creation`() {
        support.create(
            workspaceId,
            "/api/v1/projects/$projectId/achievements",
            """{"action":"first","outcome":"o","confidence":0.5}""",
        )
        support.create(
            workspaceId,
            "/api/v1/projects/$projectId/achievements",
            """{"action":"second","outcome":"o","confidence":0.5}""",
        )
        val list = support.getJson(workspaceId, "/api/v1/projects/$projectId/achievements")
        assertThat(support.titles(list, "action")).containsExactly("first", "second")

        val other = support.newWorkspace()
        client
            .get()
            .uri("/api/v1/projects/$projectId/achievements")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `update bumps revision and delete hides the achievement`() {
        val id =
            support.create(
                workspaceId,
                "/api/v1/projects/$projectId/achievements",
                """{"action":"a","outcome":"o","confidence":0.5}""",
            )
        client
            .patch()
            .uri("/api/v1/achievements/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"action":"a2","outcome":"o","confidence":0.9,"revision":1}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.revision")
            .isEqualTo(2)
            .jsonPath("$.action")
            .isEqualTo("a2")
        client
            .delete()
            .uri("/api/v1/achievements/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/projects/$projectId/achievements"), "action"),
        ).isEmpty()
    }
}
