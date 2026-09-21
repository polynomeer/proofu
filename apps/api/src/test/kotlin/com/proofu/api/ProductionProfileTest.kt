package com.proofu.api

import com.proofu.api.identity.HeaderWorkspaceResolver
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.identity.WorkspaceResolver
import com.proofu.domain.common.UserId
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.UUID

/**
 * What the `production` profile must guarantee before OIDC exists: no header identity, no seeded
 * workspace, no API docs, a real AI provider. A stand-in resolver plays the future OIDC one so
 * the context can start at all (without one, startup fails by design).
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "DATABASE_USERNAME=proofu",
        "DATABASE_PASSWORD=proofu",
        "AI_PROVIDER=anthropic",
        "ANTHROPIC_API_KEY=test-key-never-used",
    ],
)
@Import(TestcontainersConfiguration::class, ProductionProfileTest.StandInOidc::class)
@AutoConfigureRestTestClient
@ActiveProfiles("production")
class ProductionProfileTest {
    @TestConfiguration(proxyBeanMethods = false)
    class StandInOidc {
        @Bean
        fun workspaceResolver(): WorkspaceResolver =
            WorkspaceResolver { request ->
                request.getHeader("X-Test-Subject")?.let {
                    WorkspaceContext(WorkspaceId(UUID.fromString(it)), UserId(UUID.fromString(it)))
                }
            }
    }

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
        assertThat(context.containsBean("localWorkspaceSeeder")).isFalse()
        assertThat(jdbc.queryForObject("select count(*) from workspaces", Long::class.java)).isZero()
        assertThat(environment.getProperty("proofu.ai.provider")).isEqualTo("anthropic")
        assertThat(environment.getProperty("logging.level.com.proofu")).isEqualTo("info")

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
