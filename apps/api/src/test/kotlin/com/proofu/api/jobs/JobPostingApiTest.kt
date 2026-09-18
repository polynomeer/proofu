package com.proofu.api.jobs

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
class JobPostingApiTest {
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
        json: String,
        workspace: UUID = workspaceId,
    ) = client
        .post()
        .uri("/api/v1/job-postings/import")
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Suppress("UNCHECKED_CAST")
    private fun import(json: String): Map<String, Any?> {
        val body =
            post(json)
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        return mapper.readValue(body, Map::class.java) as Map<String, Any?>
    }

    private fun manual(
        text: String,
        url: String? = null,
        company: String = "ABC",
    ) = """{"source":"MANUAL_TEXT","company":"$company","roleTitle":"PM","text":${mapper.writeValueAsString(text)}
            ${if (url != null) ""","sourceUrl":"$url"""" else ""}}"""

    @Test
    fun `same text is idempotent and changed text adds a snapshot to the same url-tracked posting`() {
        val first = import(manual("Backend Engineer\r\n- Kotlin  \n", url = "https://abc.com/jobs/1"))
        assertThat(first["snapshotCreated"]).isEqualTo(true)

        val again = import(manual("Backend Engineer\n- Kotlin", url = "https://abc.com/jobs/1"))
        assertThat(again["postingId"]).isEqualTo(first["postingId"])
        assertThat(again["snapshotId"]).isEqualTo(first["snapshotId"])
        assertThat(again["snapshotCreated"]).isEqualTo(false)

        val changed = import(manual("Backend Engineer\n- Kotlin\n- 5+ years", url = "https://abc.com/jobs/1"))
        assertThat(changed["postingId"]).isEqualTo(first["postingId"])
        assertThat(changed["snapshotId"]).isNotEqualTo(first["snapshotId"])
        assertThat(changed["snapshotCreated"]).isEqualTo(true)

        val detail = support.getJson(workspaceId, "/api/v1/job-postings/${first["postingId"]}")
        assertThat(detail["snapshotCount"]).isEqualTo(2)
        val snapshots = detail["snapshots"] as List<*>
        assertThat((snapshots.first() as Map<*, *>)["id"]).isEqualTo(changed["snapshotId"])
        assertThat(
            (detail["latestSnapshot"] as Map<*, *>)["textPreview"],
        ).isEqualTo("Backend Engineer - Kotlin - 5+ years")
    }

    @Test
    fun `imports without a url create separate postings`() {
        val a = import(manual("text A"))
        val b = import(manual("text A"))
        assertThat(a["postingId"]).isNotEqualTo(b["postingId"])
        assertThat(a["contentHash"]).isEqualTo(b["contentHash"])
    }

    @Test
    fun `manual import validates its fields and other sources are not implemented`() {
        post("""{"source":"MANUAL_TEXT","text":"x"}""")
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.fieldErrors[*].field")
            .value<List<String>> { assertThat(it).containsExactlyInAnyOrder("company", "roleTitle") }
        post("""{"source":"MANUAL_TEXT","company":"A","roleTitle":"B","text":"   "}""")
            .expectStatus()
            .isBadRequest
        post("""{"source":"URL_FETCH","url":"https://abc.com/jobs/1"}""")
            .expectStatus()
            .isEqualTo(501)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_IMPLEMENTED")
        post("""{"source":"CAREER_OPS","externalPostingId":"x"}""").expectStatus().isEqualTo(501)
    }

    @Test
    fun `snapshot text is readable inside the workspace only`() {
        val r = import(manual("Line one\nLine two"))
        val snapshot = support.getJson(workspaceId, "/api/v1/job-posting-snapshots/${r["snapshotId"]}")
        assertThat(snapshot["rawText"]).isEqualTo("Line one\nLine two")
        assertThat(snapshot["postingId"]).isEqualTo(r["postingId"])

        val other = support.newWorkspace()
        client
            .get()
            .uri("/api/v1/job-posting-snapshots/${r["snapshotId"]}")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
        assertThat(support.getJson(other, "/api/v1/job-postings")["items"] as List<*>).isEmpty()
    }

    @Test
    fun `list searches company, role and text and delete keeps snapshots immutable`() {
        val a = import(manual("We use Kotlin and PostgreSQL", company = "Alpha"))
        import(manual("Python data platform", company = "Beta"))

        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/job-postings"), "company"),
        ).containsExactlyInAnyOrder("Alpha", "Beta")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/job-postings?q=kotlin"), "company"),
        ).containsExactly("Alpha")
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/job-postings?q=beta"), "company"),
        ).containsExactly("Beta")

        client
            .delete()
            .uri("/api/v1/job-postings/${a["postingId"]}")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(
            support.titles(support.getJson(workspaceId, "/api/v1/job-postings"), "company"),
        ).containsExactly("Beta")
        assertThat(
            jdbc.queryForObject(
                "select count(*) from job_posting_snapshots where posting_id = ?",
                Int::class.java,
                UUID.fromString(a["postingId"] as String),
            ),
        ).isEqualTo(1)
    }
}
