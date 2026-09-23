package com.proofu.api.capability

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
class CapabilityApiTest {
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

    private fun capability(
        name: String,
        parentId: UUID? = null,
        workspace: UUID = workspaceId,
    ) = support.create(
        workspace,
        "/api/v1/capabilities",
        """{"name":"$name","definition":"$name 을 수행한다","category":"SYSTEM_DESIGN"
            ${if (parentId != null) ""","parentId":"$parentId"""" else ""}}""",
    )

    private fun evidence(
        title: String,
        verification: String,
        workspace: UUID = workspaceId,
    ) = support.create(
        workspace,
        "/api/v1/evidence",
        """{"type":"URL","title":"$title","source":"USER_INPUT","uri":"https://example.test/$verification",
            "verification":"$verification","capturedAt":"2026-01-02T03:04:05Z"}""",
    )

    private fun put(
        path: String,
        json: String,
    ) = client
        .put()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    private fun patch(
        path: String,
        json: String,
    ) = client
        .patch()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `evidence status is derived from the links and never turns into a level`() {
        val id = capability("분산 시스템 설계")
        val fresh = support.getJson(workspaceId, "/api/v1/capabilities/$id")
        assertThat(fresh["evidenceStatus"]).isEqualTo("NONE")
        assertThat(fresh["selfAssessedLevel"]).isNull()

        val unverified = evidence("설계 문서", "UNVERIFIED")
        val verified = evidence("장애 회고", "USER_VERIFIED")
        put("/api/v1/capabilities/$id/evidence", """{"evidenceIds":["$unverified"]}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.evidenceStatus")
            .isEqualTo("UNVERIFIED")
        put("/api/v1/capabilities/$id/evidence", """{"evidenceIds":["$unverified","$verified"]}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.evidenceStatus")
            .isEqualTo("VERIFIED")
            .jsonPath("$.evidence.length()")
            .isEqualTo(2)
            .jsonPath("$.selfAssessedLevel")
            .doesNotExist()

        // Deleting the verified evidence drops the link and the status falls back.
        client
            .delete()
            .uri("/api/v1/evidence/$verified")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.getJson(workspaceId, "/api/v1/capabilities/$id")["evidenceStatus"])
            .isEqualTo("UNVERIFIED")

        // Evidence of another workspace is "not found" and changes nothing.
        val other = support.newWorkspace()
        val foreign = evidence("남의 증빙", "USER_VERIFIED", workspace = other)
        put("/api/v1/capabilities/$id/evidence", """{"evidenceIds":["$foreign"]}""").expectStatus().isNotFound
        assertThat(
            (support.getJson(workspaceId, "/api/v1/capabilities/$id")["evidence"] as List<*>),
        ).hasSize(1)
    }

    @Test
    fun `nesting refuses cycles and deleting a parent takes its subtree to the trash`() {
        val root = capability("시스템 설계")
        val child = capability("분산 설계", parentId = root)
        val grandChild = capability("합의 알고리즘", parentId = child)

        // The root may not move under its own descendant.
        patch(
            "/api/v1/capabilities/$root",
            """{"name":"시스템 설계","definition":"설계한다","category":"SYSTEM_DESIGN","parentId":"$grandChild","revision":1}""",
        ).expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
        // Nor under itself.
        patch(
            "/api/v1/capabilities/$root",
            """{"name":"시스템 설계","definition":"설계한다","category":"SYSTEM_DESIGN","parentId":"$root","revision":1}""",
        ).expectStatus().isEqualTo(422)
        // A parent in another workspace does not exist here.
        val other = support.newWorkspace()
        val foreign = capability("남의 역량", workspace = other)
        patch(
            "/api/v1/capabilities/$root",
            """{"name":"시스템 설계","definition":"설계한다","category":"SYSTEM_DESIGN","parentId":"$foreign","revision":1}""",
        ).expectStatus().isNotFound

        client
            .delete()
            .uri("/api/v1/capabilities/$root")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/capabilities"), "name")).isEmpty()
        listOf(root, child, grandChild).forEach {
            client
                .get()
                .uri("/api/v1/capabilities/$it")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .exchange()
                .expectStatus()
                .isNotFound
        }
    }

    @Test
    fun `the list filters by category and a stale revision loses`() {
        capability("시스템 설계")
        support.create(
            workspaceId,
            "/api/v1/capabilities",
            """{"name":"멘토링","definition":"동료를 성장시킨다","category":"LEADERSHIP","selfAssessedLevel":"ADVANCED",
                "evidenceCriteria":"멘토링 기록 2건 이상"}""",
        )
        val leadership = support.getJson(workspaceId, "/api/v1/capabilities?category=LEADERSHIP")
        assertThat(support.titles(leadership, "name")).containsExactly("멘토링")
        val mentoring = (leadership["items"] as List<*>).first() as Map<*, *>
        assertThat(mentoring["selfAssessedLevel"]).isEqualTo("ADVANCED")
        assertThat(mentoring["evidenceCriteria"]).isEqualTo("멘토링 기록 2건 이상")

        patch(
            "/api/v1/capabilities/${mentoring["id"]}",
            """{"name":"멘토링","definition":"동료를 성장시킨다","category":"LEADERSHIP","revision":5}""",
        ).expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")
    }
}
