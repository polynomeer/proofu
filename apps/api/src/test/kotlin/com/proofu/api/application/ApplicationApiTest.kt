package com.proofu.api.application

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
class ApplicationApiTest {
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
        val body =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"Posting text"}""")
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        snapshotId = UUID.fromString(mapper.readTree(body).get("snapshotId").asString())
    }

    private fun createApplication(): UUID =
        support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")

    private fun transition(
        id: UUID,
        to: String,
        version: Long,
        note: String? = null,
    ) = client
        .post()
        .uri("/api/v1/applications/$id/transitions")
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"to":"$to","version":$version${note?.let { ""","note":"$it"""" } ?: ""}}""")
        .exchange()

    @Test
    fun `creation copies company and role, starts INTERESTED and records the first event`() {
        val id = createApplication()
        val a = support.getJson(workspaceId, "/api/v1/applications/$id")
        assertThat(a["company"]).isEqualTo("ABC")
        assertThat(a["roleTitle"]).isEqualTo("PM")
        assertThat(a["status"]).isEqualTo("INTERESTED")
        assertThat(a["allowedTransitions"]).isEqualTo(listOf("PREPARING", "WITHDRAWN"))
        assertThat((a["events"] as List<*>).map { (it as Map<*, *>)["to"] }).containsExactly("INTERESTED")
        assertThat((a["snapshot"] as Map<*, *>)["textPreview"]).isEqualTo("Posting text")
    }

    @Test
    fun `a snapshot from another workspace is not found`() {
        client
            .post()
            .uri("/api/v1/applications")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"snapshotId":"$snapshotId"}""")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `only domain transitions succeed and history is append-only`() {
        val id = createApplication()

        transition(
            id,
            "SUBMITTED",
            1,
        ).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION")
        transition(
            id,
            "PREPARING",
            7,
        ).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("CONFLICT_STALE_VERSION")

        transition(id, "PREPARING", 1, note = "문서 작성 시작")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("PREPARING")
            .jsonPath("$.version")
            .isEqualTo(2)
            .jsonPath("$.allowedTransitions")
            .isEqualTo(listOf("SUBMITTED", "WITHDRAWN"))
        transition(id, "SUBMITTED", 2).expectStatus().isOk
        transition(id, "DOCUMENT_PASSED", 3).expectStatus().isOk
        transition(id, "HANDOFF_READY", 4)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.allowedTransitions")
            .isEqualTo(emptyList<String>())
        transition(id, "HANDED_OFF_TO_ITERVIEW", 5).expectStatus().isEqualTo(422)

        val events = support.getJson(workspaceId, "/api/v1/applications/$id")["events"] as List<*>
        assertThat(events.map { (it as Map<*, *>)["to"] })
            .containsExactly("INTERESTED", "PREPARING", "SUBMITTED", "DOCUMENT_PASSED", "HANDOFF_READY")
        assertThat((events[1] as Map<*, *>)["note"]).isEqualTo("문서 작성 시작")
    }

    @Test
    fun `board lists newest change first and can hide terminal applications`() {
        val first = createApplication()
        val second = createApplication()
        transition(first, "WITHDRAWN", 1).expectStatus().isOk

        val all = support.getJson(workspaceId, "/api/v1/applications")["items"] as List<*>
        assertThat(all.map { (it as Map<*, *>)["id"] }).containsExactly(first.toString(), second.toString())
        val active = support.getJson(workspaceId, "/api/v1/applications?includeTerminal=false")["items"] as List<*>
        assertThat(active.map { (it as Map<*, *>)["id"] }).containsExactly(second.toString())
        assertThat(support.getJson(workspaceId, "/api/v1/applications?status=WITHDRAWN")["items"] as List<*>).hasSize(1)
    }

    @Test
    fun `deadline update and delete keep the history rows`() {
        val id = createApplication()
        client
            .patch()
            .uri("/api/v1/applications/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"deadlineAt":"2026-10-01T00:00:00Z","version":1}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.deadlineAt")
            .isEqualTo("2026-10-01T00:00:00Z")
            .jsonPath("$.version")
            .isEqualTo(2)
        client
            .delete()
            .uri("/api/v1/applications/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(
            jdbc.queryForObject(
                "select count(*) from application_status_events where application_id = ?",
                Int::class.java,
                id,
            ),
        ).isEqualTo(1)
    }
}
