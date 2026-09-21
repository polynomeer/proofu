package com.proofu.api.profile

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import com.proofu.api.identity.HeaderWorkspaceResolver
import org.assertj.core.api.Assertions.assertThat
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
class ProfileApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private fun put(
        workspace: UUID,
        json: String,
    ) = client
        .put()
        .uri("/api/v1/me/profile")
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `profile is per caller, validated by the domain and versioned`() {
        val support = ApiTestSupport(client, jdbc, mapper)
        val workspace = support.newWorkspace()

        client
            .get()
            .uri("/api/v1/me/profile")
            .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
            .exchange()
            .expectStatus()
            .isNotFound

        put(workspace, """{"fullName":"  "}""").expectStatus().isBadRequest
        put(workspace, """{"fullName":"홍길동","email":"nope"}""").expectStatus().isEqualTo(422)
        put(
            workspace,
            """{"fullName":"홍길동","links":[{"label":"GitHub","url":"github.com/x"}]}""",
        ).expectStatus().isEqualTo(422)

        val created =
            mapper.readTree(
                put(
                    workspace,
                    """{"fullName":"홍길동","headline":"PM","email":"hong@example.com","phone":"010-1234-5678","location":"서울","links":[{"label":"GitHub","url":"https://github.com/hong"}]}""",
                ).expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody,
            )
        assertThat(created.get("version").asLong()).isEqualTo(1)
        assertThat(created.get("contactLine").asString()).isEqualTo("hong@example.com · 010-1234-5678 · 서울")
        assertThat(
            created
                .get("links")
                .get(0)
                .get("label")
                .asString(),
        ).isEqualTo("GitHub")

        put(workspace, """{"fullName":"홍길동","version":5}""").expectStatus().isEqualTo(409)
        val updated =
            mapper.readTree(
                put(workspace, """{"fullName":"홍길동","email":"new@example.com","version":1}""")
                    .expectStatus()
                    .isOk
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody,
            )
        assertThat(updated.get("version").asLong()).isEqualTo(2)
        assertThat(updated.get("contactLine").asString()).isEqualTo("new@example.com")
        assertThat(support.getJson(workspace, "/api/v1/me/profile")["email"]).isEqualTo("new@example.com")

        // Another caller has their own (absent) profile.
        client
            .get()
            .uri("/api/v1/me/profile")
            .header(HeaderWorkspaceResolver.HEADER, support.newWorkspace().toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
