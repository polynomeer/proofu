package com.proofu.api.identity

import com.proofu.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, WorkspaceContextTest.Probe::class)
@AutoConfigureRestTestClient
class WorkspaceContextTest {
    @RestController
    class Probe {
        @GetMapping("/api/v1/_probe/workspace")
        fun whoAmI(workspace: WorkspaceContext): Map<String, String> =
            mapOf(
                "workspaceId" to workspace.workspaceId.value.toString(),
                "userId" to workspace.userId.value.toString(),
            )
    }

    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `missing header answers 401 with UNAUTHENTICATED`() {
        client
            .get()
            .uri("/api/v1/_probe/workspace")
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("UNAUTHENTICATED")
    }

    @Test
    fun `unknown or malformed workspace answers 401`() {
        client
            .get()
            .uri("/api/v1/_probe/workspace")
            .header(HeaderWorkspaceResolver.HEADER, UUID.randomUUID().toString())
            .exchange()
            .expectStatus()
            .isUnauthorized
        client
            .get()
            .uri("/api/v1/_probe/workspace")
            .header(HeaderWorkspaceResolver.HEADER, "not-a-uuid")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `existing workspace resolves to its owner`() {
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", workspaceId, userId)

        client
            .get()
            .uri("/api/v1/_probe/workspace")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.workspaceId")
            .isEqualTo(workspaceId.toString())
            .jsonPath("$.userId")
            .isEqualTo(userId.toString())
    }
}
