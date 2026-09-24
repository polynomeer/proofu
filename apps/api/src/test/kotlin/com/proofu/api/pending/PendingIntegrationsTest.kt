package com.proofu.api.pending

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import com.proofu.api.identity.HeaderWorkspaceResolver
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

/** Declared in the contract, not built yet: the answer is "not yet" (501), never "no such path". */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class PendingIntegrationsTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var workspaceId: UUID

    @BeforeEach
    fun seed() {
        workspaceId = ApiTestSupport(client, jdbc, mapper).newWorkspace()
    }

    private fun post(
        path: String,
        json: String,
    ) = client
        .post()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()
        .expectStatus()
        .isEqualTo(501)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("NOT_IMPLEMENTED")

    @Test
    fun `file upload sessions answer 501 until the object store is chosen`() {
        post(
            "/api/v1/files/upload-sessions",
            """{"fileName":"proof.pdf","mimeType":"application/pdf","sizeBytes":1024}""",
        )
    }

    @Test
    fun `interview handoff answers 501 until the iterview contract is agreed`() {
        post("/api/v1/applications/${UUID.randomUUID()}/interview-handoffs", """{"consent":true}""")
    }
}
