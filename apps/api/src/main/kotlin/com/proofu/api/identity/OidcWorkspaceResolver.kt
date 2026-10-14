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
 * user and their single workspace. Identity is `(iss, sub)` only — an email never finds or
 * merges an account and `email_verified` is not a login condition (결정 4). Claim values are
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
        val known = provision(issuer, subject, jwt) ?: return null
        return WorkspaceContext(
            WorkspaceId(known.workspaceId),
            UserId(known.userId),
            authenticatedAt = jwt.authTime(),
            reauthenticatedAt = known.reauthenticatedAt,
            provesLogin = true,
        )
    }

    private data class Known(
        val userId: UUID,
        val workspaceId: UUID,
        val reauthenticatedAt: Instant? = null,
    )

    private fun provision(
        issuer: String,
        subject: String,
        jwt: Jwt,
    ): Known? {
        val existing =
            jdbc
                .query(
                    """
                    select u.id, u.deleted_at, u.reauthenticated_at, w.id as workspace_id from users u
                    left join workspaces w on w.owner_user_id = u.id and w.deleted_at is null
                    where u.oidc_issuer = ? and u.oidc_subject = ?
                    order by w.created_at limit 1
                    """.trimIndent(),
                    { rs, _ ->
                        Triple(
                            rs.getObject("id", UUID::class.java),
                            rs.getObject("deleted_at") != null,
                            rs.getObject("workspace_id", UUID::class.java),
                        ) to rs.getTimestamp("reauthenticated_at")?.toInstant()
                    },
                    issuer,
                    subject,
                ).firstOrNull()
        if (existing != null) {
            val (row, reauthenticatedAt) = existing
            val (userId, deleted, workspaceId) = row
            // Deleted or purging: refuse until the purge has scrubbed the subject (then it is a new user).
            if (deleted || workspaceId == null) return null
            return Known(userId, workspaceId, reauthenticatedAt)
        }
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
                    Known(userId, workspaceId)
                } else {
                    val ws =
                        jdbc.queryForObject(
                            "select id from workspaces where owner_user_id = ? order by created_at limit 1",
                            UUID::class.java,
                            actualUser,
                        )!!
                    Known(actualUser, ws)
                }
            },
        )
    }
}
