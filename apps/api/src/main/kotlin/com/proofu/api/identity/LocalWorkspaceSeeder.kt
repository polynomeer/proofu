package com.proofu.api.identity

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@ConfigurationProperties(prefix = "proofu.local-seed")
data class LocalSeedProperties(
    val enabled: Boolean = false,
    val userId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000001"),
    val workspaceId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000002"),
    val email: String = "dev@proofu.local",
    val displayName: String = "Dev User",
)

/** Creates a fixed user and workspace so local clients have something to send in X-Workspace-Id. */
@Component
@Profile("local")
@EnableConfigurationProperties(LocalSeedProperties::class)
class LocalWorkspaceSeeder(
    private val jdbc: JdbcTemplate,
    private val props: LocalSeedProperties,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(LocalWorkspaceSeeder::class.java)

    override fun run(vararg args: String) {
        if (!props.enabled) return
        jdbc.update(
            """
            insert into users (id, oidc_subject, oidc_issuer, email, display_name)
            values (?, ?, 'local', ?, ?) on conflict (id) do nothing
            """.trimIndent(),
            props.userId,
            props.userId.toString(),
            props.email,
            props.displayName,
        )
        jdbc.update(
            "insert into workspaces (id, owner_user_id, name) values (?, ?, 'Personal') on conflict (id) do nothing",
            props.workspaceId,
            props.userId,
        )
        jdbc.update(
            "insert into workspace_members (workspace_id, user_id, role) values (?, ?, 'OWNER') on conflict do nothing",
            props.workspaceId,
            props.userId,
        )
        log.info("Local workspace ready: X-Workspace-Id: {}", props.workspaceId)
    }
}
