package com.proofu.api.career

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
class CareerEntryApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var workspaceId: UUID

    @BeforeEach
    fun seedWorkspace() {
        workspaceId = newWorkspace()
    }

    @Test
    fun `create returns 201 with revision 1 and the entry is readable`() {
        val id =
            create("""{"type":"EMPLOYMENT","title":"Backend Engineer","organization":"ABC","startDate":"2022-03-01"}""")

        client
            .get()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("Backend Engineer")
            .jsonPath("$.revision")
            .isEqualTo(1)
            .jsonPath("$.visibility")
            .isEqualTo("PRIVATE")
            .jsonPath("$.status")
            .isEqualTo("ACTIVE")
            .jsonPath("$.createdAt")
            .exists()
    }

    @Test
    fun `bean validation failures list field errors`() {
        client
            .post()
            .uri("/api/v1/career-entries")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"type":"EMPLOYMENT","title":"   "}""")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.fieldErrors[*].field")
            .value<List<String>> { assertThat(it).containsExactlyInAnyOrder("title", "startDate") }
    }

    @Test
    fun `domain rule violations answer 422`() {
        client
            .post()
            .uri("/api/v1/career-entries")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"type":"EMPLOYMENT","title":"x","startDate":"2024-03-01","endDate":"2024-02-01"}""")
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
    }

    @Test
    fun `list filters by type, searches text and pages with a keyset cursor`() {
        create(
            """{"type":"EMPLOYMENT","title":"Platform Engineer","description":"Kubernetes migration","startDate":"2020-01-01"}""",
        )
        create("""{"type":"EMPLOYMENT","title":"Senior Engineer","startDate":"2022-01-01"}""")
        create("""{"type":"CERTIFICATION","title":"PMP","startDate":"2019-08-01"}""")

        val firstPage = list("limit=2")
        val items = firstPage["items"] as List<*>
        assertThat(items.map { (it as Map<*, *>)["title"] }).containsExactly("Senior Engineer", "Platform Engineer")
        val next = firstPage["nextCursor"] as String

        val secondPage = list("limit=2&cursor=$next")
        assertThat((secondPage["items"] as List<*>).map { (it as Map<*, *>)["title"] }).containsExactly("PMP")
        assertThat(secondPage["nextCursor"]).isNull()

        assertThat((list("type=CERTIFICATION")["items"] as List<*>)).hasSize(1)
        assertThat((list("q=kubernetes")["items"] as List<*>).map { (it as Map<*, *>)["title"] })
            .containsExactly("Platform Engineer")
    }

    @Test
    fun `malformed cursor answers 400`() {
        client
            .get()
            .uri("/api/v1/career-entries?cursor=%%%")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `update requires the current revision and bumps it`() {
        val id = create("""{"type":"EMPLOYMENT","title":"Engineer","startDate":"2022-01-01"}""")
        val body = """{"type":"EMPLOYMENT","title":"Staff Engineer","startDate":"2022-01-01","revision":%d}"""

        client
            .patch()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body.format(1))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("Staff Engineer")
            .jsonPath("$.revision")
            .isEqualTo(2)

        client
            .patch()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body.format(1))
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")
    }

    @Test
    fun `delete is soft and hides the entry`() {
        val id = create("""{"type":"AWARD","title":"Hackathon winner","startDate":"2021-05-01"}""")

        client
            .delete()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        client
            .get()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNotFound

        val row = jdbc.queryForMap("select status, deleted_at from career_entries where id = ?", id)
        assertThat(row["status"]).isEqualTo("DELETED")
        assertThat(row["deleted_at"]).isNotNull
    }

    @Test
    fun `entries of another workspace are reported as not found`() {
        val id = create("""{"type":"EMPLOYMENT","title":"Engineer","startDate":"2022-01-01"}""")
        val other = newWorkspace()

        client
            .get()
            .uri("/api/v1/career-entries/$id")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_FOUND")
        assertThat(list("", other)["items"] as List<*>).isEmpty()
    }

    @Test
    fun `mutations leave audit events with hashes only`() {
        val id = create("""{"type":"EMPLOYMENT","title":"Engineer","startDate":"2022-01-01"}""")
        val rows =
            jdbc.queryForList(
                "select action, before_hash, after_hash from audit_events where target_id = ? order by occurred_at",
                id,
            )
        assertThat(rows).hasSize(1)
        assertThat(rows[0]["action"]).isEqualTo("career_entry.created")
        assertThat(rows[0]["before_hash"]).isNull()
        assertThat(rows[0]["after_hash"] as String).hasSize(64)
    }

    private fun create(json: String): UUID {
        val body =
            client
                .post()
                .uri("/api/v1/career-entries")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        return UUID.fromString(mapper.readTree(body).get("id").asString())
    }

    private fun list(
        query: String,
        workspace: UUID = workspaceId,
    ): Map<String, Any?> {
        val body =
            client
                .get()
                .uri("/api/v1/career-entries?$query")
                .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        @Suppress("UNCHECKED_CAST")
        return mapper.readValue(body, Map::class.java) as Map<String, Any?>
    }

    private fun newWorkspace(): UUID {
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", id, userId)
        return id
    }
}
