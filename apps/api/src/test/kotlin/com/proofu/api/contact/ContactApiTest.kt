package com.proofu.api.contact

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
class ContactApiTest {
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

    private fun post(
        workspace: UUID,
        path: String,
        json: String,
    ) = client
        .post()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
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

    private fun careerEntry(): UUID =
        support.create(
            workspaceId,
            "/api/v1/career-entries",
            """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2020-01-01"}""",
        )

    private fun project(entryId: UUID): UUID =
        support.create(
            workspaceId,
            "/api/v1/projects",
            """{"careerEntryId":"$entryId","name":"Checkout","role":"Lead","summary":"Rebuilt checkout"}""",
        )

    @Test
    fun `a contact keeps where they worked together, and an address only decides reachability`() {
        val entry = careerEntry()
        val projectId = project(entry)
        val id =
            support.create(
                workspaceId,
                "/api/v1/contacts",
                """{"displayName":"김동료","relation":"PEER","organization":"ACME","role":"백엔드",
                    "careerEntryIds":["$entry"],"projectIds":["$projectId"]}""",
            )
        val contact = support.getJson(workspaceId, "/api/v1/contacts/$id")
        assertThat(contact["displayName"]).isEqualTo("김동료")
        assertThat(contact["relation"]).isEqualTo("PEER")
        assertThat(contact["careerEntryIds"] as List<*>).containsExactly(entry.toString())
        assertThat(contact["projectIds"] as List<*>).containsExactly(projectId.toString())
        // No address yet: this person cannot be asked for an attestation.
        assertThat(contact["reachable"]).isEqualTo(false)
        assertThat(contact["email"]).isNull()

        val withEmail =
            patch(
                "/api/v1/contacts/$id",
                """{"displayName":"김동료","relation":"PEER","email":"peer@example.test","revision":1}""",
            ).expectStatus()
                .isOk
                .expectBody(Map::class.java)
                .returnResult()
                .responseBody!!
        assertThat(withEmail["reachable"]).isEqualTo(true)
        // The links were replaced as a whole set, so leaving them out clears them.
        assertThat(withEmail["careerEntryIds"] as List<*>).isEmpty()
        assertThat(withEmail["revision"]).isEqualTo(2)
    }

    @Test
    fun `a name is required and an address must look like one`() {
        post(workspaceId, "/api/v1/contacts", """{"displayName":"  ","relation":"PEER"}""")
            .expectStatus()
            .isEqualTo(400)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_FAILED")
        post(
            workspaceId,
            "/api/v1/contacts",
            """{"displayName":"김동료","relation":"PEER","email":"not-an-address"}""",
        ).expectStatus()
            .isEqualTo(400)
        post(workspaceId, "/api/v1/contacts", """{"displayName":"김동료","relation":"FRIEND"}""")
            .expectStatus()
            .isEqualTo(400)
    }

    @Test
    fun `a stale revision loses and another workspace's record cannot be linked`() {
        val id =
            support.create(workspaceId, "/api/v1/contacts", """{"displayName":"박상사","relation":"MANAGER"}""")
        patch("/api/v1/contacts/$id", """{"displayName":"박상사","relation":"MANAGER","revision":1}""")
            .expectStatus()
            .isOk
        patch("/api/v1/contacts/$id", """{"displayName":"박상사","relation":"MANAGER","revision":1}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")

        val foreign = support.newWorkspace()
        val foreignEntry =
            support.create(
                foreign,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"Elsewhere","startDate":"2020-01-01"}""",
            )
        post(
            workspaceId,
            "/api/v1/contacts",
            """{"displayName":"남의 동료","relation":"PEER","careerEntryIds":["$foreignEntry"]}""",
        ).expectStatus()
            .isEqualTo(404)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_FOUND")
    }

    @Test
    fun `the list filters by relation and pages with a cursor`() {
        listOf("PEER" to "동료1", "MANAGER" to "상사", "PEER" to "동료2").forEach { (relation, name) ->
            support.create(
                workspaceId,
                "/api/v1/contacts",
                """{"displayName":"$name","relation":"$relation"}""",
            )
        }
        val all = support.getJson(workspaceId, "/api/v1/contacts")
        assertThat(support.titles(all, "displayName")).containsExactly("동료2", "상사", "동료1")

        val peers = support.getJson(workspaceId, "/api/v1/contacts?relation=PEER")
        assertThat(support.titles(peers, "displayName")).containsExactly("동료2", "동료1")

        val firstPage = support.getJson(workspaceId, "/api/v1/contacts?limit=2")
        assertThat(support.titles(firstPage, "displayName")).containsExactly("동료2", "상사")
        val nextPage = support.getJson(workspaceId, "/api/v1/contacts?limit=2&cursor=${firstPage["nextCursor"]}")
        assertThat(support.titles(nextPage, "displayName")).containsExactly("동료1")
        assertThat(nextPage["nextCursor"]).isNull()
    }

    /** Third-party data stays where it was entered: the contact list is the only way to find one. */
    @Test
    fun `a contact never shows up in the global search`() {
        support.create(
            workspaceId,
            "/api/v1/contacts",
            """{"displayName":"코틀린특이값동료","relation":"PEER","organization":"코틀린특이값"}""",
        )
        val results =
            client
                .get()
                .uri("/api/v1/search?q=코틀린특이값")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        assertThat(results).doesNotContain("코틀린특이값동료")
    }

    @Test
    fun `a deleted contact leaves the list and another workspace never sees it`() {
        val id =
            support.create(workspaceId, "/api/v1/contacts", """{"displayName":"지울 사람","relation":"OTHER"}""")
        val other = support.newWorkspace()
        client
            .get()
            .uri("/api/v1/contacts/$id")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isEqualTo(404)

        client
            .delete()
            .uri("/api/v1/contacts/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/contacts"), "displayName")).isEmpty()
        assertThat(
            jdbc.queryForObject("select count(*) from contacts where id = ?", Long::class.java, id),
        ).isEqualTo(1)
    }
}
