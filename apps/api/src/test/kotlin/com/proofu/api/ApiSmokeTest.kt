package com.proofu.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class ApiSmokeTest {
    @Autowired
    lateinit var client: RestTestClient

    @Test
    fun `health endpoint reports UP`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `openapi document is served under the api prefix`() {
        client
            .get()
            .uri("/api/v1/openapi.json")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.info.title")
            .isEqualTo("ProofU API")
    }

    @Test
    fun `unknown routes answer with problem details and a code`() {
        client
            .get()
            .uri("/api/v1/does-not-exist")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_FOUND")
            .jsonPath("$.status")
            .isEqualTo(404)
    }
}
