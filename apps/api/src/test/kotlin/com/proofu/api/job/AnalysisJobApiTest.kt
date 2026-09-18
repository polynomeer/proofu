package com.proofu.api.job

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
class AnalysisJobApiTest {
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
        val imported =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"Kotlin 5+ years"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        snapshotId = UUID.fromString(mapper.readTree(imported).get("snapshotId").asString())
    }

    private fun start(workspace: UUID = workspaceId) =
        client
            .post()
            .uri("/api/v1/job-posting-snapshots/$snapshotId/analysis-jobs")
            .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
            .exchange()

    private fun jobIdOf(body: String?) = mapper.readTree(body).get("jobId").asString()

    @Test
    fun `analysis is accepted as a queued job that the workspace can poll`() {
        val jobId =
            jobIdOf(
                start()
                    .expectStatus()
                    .isAccepted
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody,
            )

        val job = support.getJson(workspaceId, "/api/v1/jobs/$jobId")
        assertThat(job["type"]).isEqualTo("posting.analysis")
        assertThat(job["status"]).isEqualTo("QUEUED")
        assertThat(job["attempts"]).isEqualTo(0)
        assertThat(
            jdbc.queryForObject(
                "select payload ->> 'snapshotId' from jobs where id = ?",
                String::class.java,
                UUID.fromString(jobId),
            ),
        ).isEqualTo(snapshotId.toString())
    }

    @Test
    fun `a pending analysis for the same snapshot is not duplicated`() {
        val first = jobIdOf(start().expectBody(String::class.java).returnResult().responseBody)
        val second = jobIdOf(start().expectBody(String::class.java).returnResult().responseBody)
        assertThat(second).isEqualTo(first)

        jdbc.update("update jobs set status = 'SUCCEEDED' where id = ?", UUID.fromString(first))
        val third = jobIdOf(start().expectBody(String::class.java).returnResult().responseBody)
        assertThat(third).isNotEqualTo(first)
    }

    @Test
    fun `snapshots and jobs of other workspaces are not found`() {
        val other = support.newWorkspace()
        start(other).expectStatus().isNotFound
        val jobId = jobIdOf(start().expectBody(String::class.java).returnResult().responseBody)
        client
            .get()
            .uri("/api/v1/jobs/$jobId")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
