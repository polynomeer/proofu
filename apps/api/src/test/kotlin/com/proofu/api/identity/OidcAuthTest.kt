package com.proofu.api.identity

import com.proofu.api.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** ADR-0010 결정 1: bearer JWTs, first-login provisioning, isolation and step-up — with any OIDC issuer. */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["proofu.auth.mode=oidc", "proofu.auth.reauth-max-age=10m"],
)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class OidcAuthTest {
    companion object {
        private val idp = FakeIdp()

        @JvmStatic
        @DynamicPropertySource
        fun idp(registry: DynamicPropertyRegistry) {
            registry.add("proofu.auth.issuer") { idp.issuer }
            registry.add("proofu.auth.jwks-uri") { idp.jwksUri }
        }

        @JvmStatic
        @AfterAll
        fun stop() = idp.close()
    }

    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun get(
        path: String,
        token: String?,
    ) = client
        .get()
        .uri(path)
        .apply { if (token != null) header("Authorization", "Bearer $token") }
        .exchange()

    @Test
    fun `a verified token provisions the user once and scopes every call to their workspace`() {
        val subject = UUID.randomUUID().toString()
        get(
            "/api/v1/career-entries",
            null,
        ).expectStatus().isUnauthorized.expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
        get("/api/v1/career-entries", idp.token(subject)).expectStatus().isOk
        get("/api/v1/career-entries", idp.token(subject)).expectStatus().isOk
        val users = jdbc.queryForObject("select count(*) from users where oidc_subject = ?", Long::class.java, subject)
        assertThat(users).isEqualTo(1L)
        val workspaces =
            jdbc.queryForObject(
                "select count(*) from workspaces w join users u on u.id = w.owner_user_id where u.oidc_subject = ?",
                Long::class.java,
                subject,
            )
        assertThat(workspaces).isEqualTo(1L)

        // The interim header means nothing here.
        client
            .get()
            .uri("/api/v1/career-entries")
            .header(HeaderWorkspaceResolver.HEADER, "00000000-0000-7000-8000-000000000002")
            .exchange()
            .expectStatus()
            .isUnauthorized

        // Data written by one subject is invisible to another.
        client
            .post()
            .uri("/api/v1/career-entries")
            .header("Authorization", "Bearer ${idp.token(subject)}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"type":"EMPLOYMENT","title":"Mine","startDate":"2022-01-01"}""")
            .exchange()
            .expectStatus()
            .isCreated
        get(
            "/api/v1/career-entries",
            idp.token(subject),
        ).expectStatus().isOk.expectBody().jsonPath("$.items.length()").isEqualTo(1)
        get("/api/v1/career-entries", idp.token(UUID.randomUUID().toString()))
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(0)
    }

    @Test
    fun `tokens with the wrong audience or issuer, or an unverified email, are refused`() {
        get("/api/v1/career-entries", idp.token(audience = "someone-else")).expectStatus().isUnauthorized
        get("/api/v1/career-entries", idp.token(issuer = "https://evil.example")).expectStatus().isUnauthorized
        get("/api/v1/career-entries", idp.token(emailVerified = false)).expectStatus().isUnauthorized
        get(
            "/api/v1/career-entries",
            idp.token(expiresAt = Instant.now().minusSeconds(60)),
        ).expectStatus().isUnauthorized
        get("/actuator/health", null).expectStatus().isOk
    }

    @Test
    fun `changing the profile email needs a recent login`() {
        val subject = UUID.randomUUID().toString()

        fun save(
            json: String,
            authTime: Instant,
        ) = client
            .put()
            .uri("/api/v1/me/profile")
            .header("Authorization", "Bearer ${idp.token(subject, authTime = authTime)}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

        val stale = Instant.now().minus(Duration.ofHours(1))
        // First save: nothing to protect yet.
        save("""{"fullName":"홍길동","email":"a@example.com"}""", stale).expectStatus().isOk
        save("""{"fullName":"홍길동","email":"b@example.com","version":1}""", stale)
            .expectStatus()
            .isUnauthorized
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("REAUTHENTICATION_REQUIRED")
        // Same email, other fields: no step-up needed.
        save("""{"fullName":"홍길동 2","email":"a@example.com","version":1}""", stale).expectStatus().isOk
        save("""{"fullName":"홍길동","email":"b@example.com","version":2}""", Instant.now()).expectStatus().isOk
    }
}
