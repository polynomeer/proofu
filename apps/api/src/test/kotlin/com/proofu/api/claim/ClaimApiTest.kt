package com.proofu.api.claim

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
class ClaimApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var projectId: UUID
    private lateinit var achievementId: UUID
    private lateinit var evidenceId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
        projectId = support.create(workspaceId, "/api/v1/projects", """{"name":"P","role":"r","summary":"s"}""")
        achievementId =
            support.create(
                workspaceId,
                "/api/v1/projects/$projectId/achievements",
                """{"action":"Cut build time","outcome":"CI twice as fast","metricValue":50,"metricUnit":"%","confidence":0.9}""",
            )
        evidenceId =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"URL","title":"CI dashboard","uri":"https://ci.example.com"}""",
            )
    }

    private fun claimJson(sourceId: UUID = achievementId) =
        """{"text":"Cut CI build time by 50%","type":"FACT","sources":[{"type":"ACHIEVEMENT","id":"$sourceId"}]}"""

    private fun link(
        claimId: UUID,
        json: String,
    ) = client
        .post()
        .uri("/api/v1/claims/$claimId/evidence")
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `claim pins its source revision and starts unsupported`() {
        val id = support.create(workspaceId, "/api/v1/claims", claimJson())
        val claim = support.getJson(workspaceId, "/api/v1/claims/$id")
        assertThat(claim["status"]).isEqualTo("UNSUPPORTED")
        val sources = claim["sources"] as List<*>
        assertThat((sources.single() as Map<*, *>)["revision"]).isEqualTo(1)
        assertThat((sources.single() as Map<*, *>)["id"]).isEqualTo(achievementId.toString())
        assertThat((sources.single() as Map<*, *>)["title"]).isEqualTo("Cut build time")
        assertThat((sources.single() as Map<*, *>)["projectId"]).isEqualTo(projectId.toString())
    }

    @Test
    fun `a source from another workspace is not found`() {
        client
            .post()
            .uri("/api/v1/claims")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(claimJson())
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `linking evidence derives the status and unlinking reverts it`() {
        val id = support.create(workspaceId, "/api/v1/claims", claimJson())

        link(id, """{"evidenceId":"$evidenceId","relation":"PARTIALLY_SUPPORTS","confidence":0.7}""")
            .expectStatus()
            .isEqualTo(422)

        link(id, """{"evidenceId":"$evidenceId","relation":"SUPPORTS","confidence":0.7}""")
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("SUPPORTED")
            .jsonPath("$.links[0].evidenceTitle")
            .isEqualTo("CI dashboard")

        val refuting =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"NOTE","title":"Counter","body":"Build time regressed later"}""",
            )
        link(id, """{"evidenceId":"$refuting","relation":"REFUTES","confidence":0.5}""")
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("CONTESTED")

        assertThat(support.getJson(workspaceId, "/api/v1/evidence/$evidenceId")["linkedClaimCount"]).isEqualTo(1)
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/evidence/$evidenceId/claims"), "text"))
            .containsExactly("Cut CI build time by 50%")

        client
            .delete()
            .uri("/api/v1/claims/$id/evidence/$refuting")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("SUPPORTED")
        client
            .delete()
            .uri("/api/v1/claims/$id/evidence/$evidenceId")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UNSUPPORTED")
    }

    @Test
    fun `evidence of another workspace cannot be linked`() {
        val id = support.create(workspaceId, "/api/v1/claims", claimJson())
        val other = support.newWorkspace()
        val foreign = support.create(other, "/api/v1/evidence", """{"type":"URL","title":"x","uri":"https://a"}""")
        link(id, """{"evidenceId":"$foreign","relation":"SUPPORTS","confidence":0.7}""").expectStatus().isNotFound
    }

    @Test
    fun `deleting evidence drops its links and the claim becomes unsupported`() {
        val id = support.create(workspaceId, "/api/v1/claims", claimJson())
        link(id, """{"evidenceId":"$evidenceId","relation":"SUPPORTS","confidence":0.7}""").expectStatus().isCreated
        client
            .delete()
            .uri("/api/v1/evidence/$evidenceId")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.getJson(workspaceId, "/api/v1/claims/$id")["status"]).isEqualTo("UNSUPPORTED")
    }

    @Test
    fun `list by project includes claims about its achievements and update bumps revision`() {
        val byAchievement = support.create(workspaceId, "/api/v1/claims", claimJson())
        val byProject =
            support.create(
                workspaceId,
                "/api/v1/claims",
                """{"text":"Led the project","type":"FACT","sources":[{"type":"PROJECT","id":"$projectId"}]}""",
            )
        val list = support.getJson(workspaceId, "/api/v1/claims?projectId=$projectId")
        assertThat((list["items"] as List<*>).map { (it as Map<*, *>)["id"] })
            .containsExactly(byAchievement.toString(), byProject.toString())
        assertThat(
            support.titles(
                support.getJson(workspaceId, "/api/v1/claims?sourceType=ACHIEVEMENT&sourceId=$achievementId"),
                "text",
            ),
        ).containsExactly("Cut CI build time by 50%")

        client
            .patch()
            .uri("/api/v1/claims/$byProject")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"text":"Led the project end to end","type":"FACT","revision":1}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.revision")
            .isEqualTo(2)
            .jsonPath("$.sources.length()")
            .isEqualTo(1)
    }
}
