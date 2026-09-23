package com.proofu.api.web

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

/** Request ids tie a log line to the audit row it produced (docs/operations/monitoring.md). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class RequestLogFilterTest {
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

    private fun skills(requestId: String?) =
        client
            .get()
            .uri("/api/v1/skills")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .apply { if (requestId != null) header(RequestLogFilter.HEADER, requestId) }
            .exchange()
            .expectStatus()
            .isOk
            .returnResult(String::class.java)
            .responseHeaders
            .getFirst(RequestLogFilter.HEADER)

    @Test
    fun `a caller's request id is echoed and recorded, a missing one is generated`() {
        val mine = "req-abc_123.4"
        client
            .post()
            .uri("/api/v1/skills")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .header(RequestLogFilter.HEADER, mine)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE"}""")
            .exchange()
            .expectStatus()
            .isCreated
            .expectHeader()
            .valueEquals(RequestLogFilter.HEADER, mine)
        assertThat(
            jdbc.queryForObject(
                "select request_id from audit_events where workspace_id = ? order by occurred_at desc limit 1",
                String::class.java,
                workspaceId,
            ),
        ).isEqualTo(mine)

        val generated = skills(requestId = null)
        assertThat(generated).isNotBlank()
        assertThat(UUID.fromString(generated)).isNotNull()
    }

    @Test
    fun `an unusable id from the caller is replaced instead of being taken as given`() {
        assertThat(RequestLogFilter.acceptable("fine-1")).isEqualTo("fine-1")
        assertThat(RequestLogFilter.acceptable("has space")).isNull()
        assertThat(RequestLogFilter.acceptable("a".repeat(65))).isNull()
        assertThat(RequestLogFilter.acceptable("line\nbreak")).isNull()
        assertThat(RequestLogFilter.acceptable(null)).isNull()

        val echoed = skills(requestId = "not_ok!")
        assertThat(echoed).isNotEqualTo("not_ok!")
        assertThat(UUID.fromString(echoed)).isNotNull()
    }

    @Test
    fun `metrics are exposed with route templates and carry no workspace label`() {
        support.create(workspaceId, "/api/v1/skills", """{"canonicalName":"Postgres","category":"DATA"}""")
        val body =
            client
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        assertThat(body).contains("http_server_requests_seconds")
        assertThat(body).contains("""uri="/api/v1/skills"""")
        assertThat(body).doesNotContain(workspaceId.toString())
        assertThat(body).doesNotContain("workspace")
    }
}
