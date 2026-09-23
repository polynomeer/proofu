package com.proofu.api

import com.proofu.api.identity.HeaderWorkspaceResolver
import com.proofu.api.identity.OidcWorkspaceResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * What the `production` profile must guarantee: OIDC only (no header identity), no seeded
 * workspace, no API docs, a real AI provider. The IdP need not be reachable at startup — the
 * JWKS is fetched on the first token — so every request is simply refused here.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "DATABASE_USERNAME=proofu",
        "DATABASE_PASSWORD=proofu",
        "AI_PROVIDER=anthropic",
        "ANTHROPIC_API_KEY=test-key-never-used",
        "OIDC_ISSUER=https://idp.example/realms/proofu",
        "OIDC_JWKS_URI=https://idp.example/realms/proofu/protocol/openid-connect/certs",
    ],
)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@ActiveProfiles("production")
class ProductionProfileTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    lateinit var environment: Environment

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `header identity, seeding and docs are off and the AI provider is real`() {
        assertThat(context.getBeanNamesForType(HeaderWorkspaceResolver::class.java)).isEmpty()
        assertThat(context.getBeanNamesForType(OidcWorkspaceResolver::class.java)).hasSize(1)
        assertThat(environment.getProperty("proofu.auth.mode")).isEqualTo("oidc")
        assertThat(context.containsBean("localWorkspaceSeeder")).isFalse()
        assertThat(jdbc.queryForObject("select count(*) from workspaces", Long::class.java)).isZero()
        assertThat(environment.getProperty("proofu.ai.provider")).isEqualTo("anthropic")
        assertThat(environment.getProperty("logging.level.com.proofu")).isEqualTo("info")
        // Health and the metrics scrape stay on; deployments move them off the public port.
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
            .isEqualTo("health,info,prometheus")
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never")

        // The interim header no longer identifies anyone.
        client
            .get()
            .uri("/api/v1/career-entries")
            .header(HeaderWorkspaceResolver.HEADER, "00000000-0000-7000-8000-000000000002")
            .exchange()
            .expectStatus()
            .isUnauthorized
        client
            .get()
            .uri("/api/v1/openapi.json")
            .exchange()
            .expectStatus()
            .isNotFound
        client
            .get()
            .uri("/api/v1/docs")
            .exchange()
            .expectStatus()
            .isNotFound
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
    }
}
