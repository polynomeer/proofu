package com.proofu.api.identity

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
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

/** The header auth mode (local and test) has no login, so there is nothing to prove (ADR-0010 결정 3). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class ReauthenticationHeaderModeTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `the header auth mode refuses a re-authentication and validates the body first`() {
        val workspace = ApiTestSupport(client, jdbc, mapper).newWorkspace()

        fun post(json: String) =
            client
                .post()
                .uri("/api/v1/me/reauthentications")
                .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()

        post("""{}""").expectStatus().isBadRequest
        post("""{"idToken":"eyJ.a.b"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
    }
}
