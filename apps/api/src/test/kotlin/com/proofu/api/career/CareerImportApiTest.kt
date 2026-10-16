package com.proofu.api.career

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
class CareerImportApiTest {
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

    private fun import(
        body: String,
        workspace: UUID = workspaceId,
    ) = client
        .post()
        .uri("/api/v1/career-imports")
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    @Test
    fun `a pasted resume becomes a queued job and no career data`() {
        val body =
            import("""{"text":"2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어"}""")
                .expectStatus()
                .isAccepted
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val jobId = UUID.fromString(mapper.readTree(body).get("jobId").asString())

        val row =
            jdbc.queryForMap(
                "select type, status, workspace_id from jobs where id = ?",
                jobId,
            )
        assertThat(row["type"]).isEqualTo("career.import")
        assertThat(row["status"]).isEqualTo("QUEUED")
        assertThat(row["workspace_id"]).isEqualTo(workspaceId)
        // The entries appear only when the user picks them; the endpoint itself writes none.
        assertThat(
            jdbc.queryForObject(
                "select count(*) from career_entries where workspace_id = ?",
                Long::class.java,
                workspaceId,
            ),
        ).isZero()
    }

    @Test
    fun `an empty or missing resume is a validation failure, not a job`() {
        import("""{"text":"   "}""").expectStatus().isBadRequest
        import("""{"text":"짧다"}""").expectStatus().isBadRequest
        import("""{}""").expectStatus().isBadRequest
        assertThat(
            jdbc.queryForObject(
                "select count(*) from jobs where workspace_id = ? and type = 'career.import'",
                Long::class.java,
                workspaceId,
            ),
        ).isZero()
    }
}
