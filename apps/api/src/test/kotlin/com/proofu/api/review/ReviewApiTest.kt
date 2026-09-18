package com.proofu.api.review

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
class ReviewApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID
    private lateinit var applicationId: UUID

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
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"text"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val snapshotId = mapper.readTree(imported).get("snapshotId").asString()
        applicationId = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
    }

    private fun move(
        to: String,
        version: Long,
    ) = client
        .post()
        .uri("/api/v1/applications/$applicationId/transitions")
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"to":"$to","version":$version}""")
        .exchange()
        .expectStatus()
        .isOk

    private fun postReview(json: String) =
        client
            .post()
            .uri("/api/v1/applications/$applicationId/reviews")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    private val review = """{"observedFact":"제출 8일 후 불합격 이메일","hypothesis":"필수 경력 근거가 약했을 수 있음","confidence":"MEDIUM",
        "improvementAction":"운영 성과 Evidence 우선 연결","verificationPlan":"다음 3건 비교"}"""

    @Test
    fun `reviews are rejected before a document result`() {
        postReview(
            review,
        ).expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo("DOMAIN_RULE_VIOLATION")
    }

    @Test
    fun `first review walks a rejected application to REVIEWED and flags definitive wording`() {
        move("PREPARING", 1)
        move("SUBMITTED", 2)
        move("DOCUMENT_REJECTED", 3)

        postReview(review)
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.warnings")
            .isEqualTo(emptyList<String>())
            .jsonPath("$.confidence")
            .isEqualTo("MEDIUM")

        val a = support.getJson(workspaceId, "/api/v1/applications/$applicationId")
        assertThat(a["status"]).isEqualTo("REVIEWED")
        assertThat((a["events"] as List<*>).map { (it as Map<*, *>)["to"] })
            .containsExactly("INTERESTED", "PREPARING", "SUBMITTED", "DOCUMENT_REJECTED", "REVIEW_PENDING", "REVIEWED")

        postReview("""{"observedFact":"사실","hypothesis":"경력 연수 때문에 탈락했다","rationale":"원인은 분명히 그것"}""")
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.warnings")
            .isEqualTo(listOf("때문에 탈락", "분명히", "원인은"))

        val list = support.getJson(workspaceId, "/api/v1/applications/$applicationId/reviews")
        assertThat(list["items"] as List<*>).hasSize(2)
    }

    @Test
    fun `update checks version and delete removes the review`() {
        move("PREPARING", 1)
        move("SUBMITTED", 2)
        move("NO_RESPONSE", 3)
        val id = support.create(workspaceId, "/api/v1/applications/$applicationId/reviews", review)

        client
            .patch()
            .uri("/api/v1/reviews/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"observedFact":"사실","hypothesis":"다른 가설","confidence":"HIGH","version":1}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.version")
            .isEqualTo(2)
            .jsonPath("$.hypothesis")
            .isEqualTo("다른 가설")

        client
            .get()
            .uri("/api/v1/reviews/$id")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound

        client
            .delete()
            .uri("/api/v1/reviews/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(
            support.getJson(workspaceId, "/api/v1/applications/$applicationId/reviews")["items"] as List<*>,
        ).isEmpty()
    }
}
