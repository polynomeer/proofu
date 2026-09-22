package com.proofu.api.identity

import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.UserId
import com.proofu.domain.common.WorkspaceId
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * ADR-0010 결정 1 §2: the verified JWT names the user; `(iss, sub)` finds or provisions the
 * user and their single workspace. Unverified emails are not let in (§7). Claim values are
 * never logged.
 */
@Component
@ConditionalOnProperty(prefix = "proofu.auth", name = ["mode"], havingValue = "oidc")
class OidcWorkspaceResolver(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val ids: IdGenerator,
) : WorkspaceResolver {
    private val log = LoggerFactory.getLogger(OidcWorkspaceResolver::class.java)

    override fun resolve(request: HttpServletRequest): WorkspaceContext? {
        val jwt = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token ?: return null
        val issuer = jwt.issuer?.toString() ?: return null
        val subject = jwt.subject ?: return null
        if (jwt.getClaimAsBoolean("email_verified") == false) return null
        val (userId, workspaceId) = provision(issuer, subject, jwt)
        return WorkspaceContext(WorkspaceId(workspaceId), UserId(userId), authenticatedAt = authTime(jwt))
    }

    private fun provision(
        issuer: String,
        subject: String,
        jwt: Jwt,
    ): Pair<UUID, UUID> {
        val existing =
            jdbc
                .query(
                    """
                    select u.id, w.id as workspace_id from users u
                    join workspaces w on w.owner_user_id = u.id and w.deleted_at is null
                    where u.oidc_issuer = ? and u.oidc_subject = ? and u.deleted_at is null
                    order by w.created_at limit 1
                    """.trimIndent(),
                    { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getObject("workspace_id", UUID::class.java) },
                    issuer,
                    subject,
                ).firstOrNull()
        if (existing != null) return existing
        return checkNotNull(
            tx.execute {
                val userId = ids.next()
                val workspaceId = ids.next()
                val email = jwt.getClaimAsString("email") ?: "$subject@unknown.invalid"
                val name =
                    jwt.getClaimAsString("name") ?: jwt.getClaimAsString("preferred_username")
                        ?: email.substringBefore('@')
                jdbc.update(
                    """
                    insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, ?, ?, ?)
                    on conflict (oidc_issuer, oidc_subject) do nothing
                    """.trimIndent(),
                    userId,
                    subject,
                    issuer,
                    email.take(320),
                    name.take(120),
                )
                // A concurrent first request may have won the insert; read back whichever row exists.
                val actualUser =
                    jdbc.queryForObject(
                        "select id from users where oidc_issuer = ? and oidc_subject = ?",
                        UUID::class.java,
                        issuer,
                        subject,
                    )!!
                if (actualUser == userId) {
                    jdbc.update(
                        "insert into workspaces (id, owner_user_id, name) values (?, ?, 'Personal')",
                        workspaceId,
                        userId,
                    )
                    jdbc.update(
                        "insert into workspace_members (workspace_id, user_id, role) values (?, ?, 'OWNER')",
                        workspaceId,
                        userId,
                    )
                    log.info("Provisioned a new user and workspace from {}", issuer)
                    userId to workspaceId
                } else {
                    val ws =
                        jdbc.queryForObject(
                            "select id from workspaces where owner_user_id = ? order by created_at limit 1",
                            UUID::class.java,
                            actualUser,
                        )!!
                    actualUser to ws
                }
            },
        )
    }

    private fun authTime(jwt: Jwt): Instant? =
        (jwt.getClaim<Any>("auth_time") as? Instant)
            ?: (jwt.getClaim<Any>("auth_time") as? Number)?.let { Instant.ofEpochSecond(it.toLong()) }
            ?: jwt.issuedAt
}
