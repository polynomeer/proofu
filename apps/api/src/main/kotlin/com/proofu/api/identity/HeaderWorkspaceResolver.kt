package com.proofu.api.identity

import com.proofu.domain.common.UserId
import com.proofu.domain.common.WorkspaceId
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Interim resolver until the OIDC provider is chosen (docs/project/open-decisions.md):
 * the caller names a workspace with `X-Workspace-Id` and is treated as its owner.
 *
 * Never active under the `production` profile, so a production deployment without a
 * real resolver fails to start instead of trusting client-supplied identifiers.
 */
@Component
@Profile("!production")
@ConditionalOnProperty(prefix = "proofu.auth", name = ["mode"], havingValue = "header", matchIfMissing = true)
class HeaderWorkspaceResolver(
    private val jdbc: JdbcTemplate,
) : WorkspaceResolver {
    private val log = LoggerFactory.getLogger(HeaderWorkspaceResolver::class.java)

    @PostConstruct
    fun warn() {
        log.warn("Authentication is header based ($HEADER). Do not expose this instance publicly.")
    }

    override fun resolve(request: HttpServletRequest): WorkspaceContext? {
        val workspaceId =
            request.getHeader(HEADER)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val ownerId =
            jdbc
                .query(
                    "select owner_user_id from workspaces where id = ? and deleted_at is null",
                    { rs, _ -> rs.getObject(1, UUID::class.java) },
                    workspaceId,
                ).firstOrNull() ?: return null
        return WorkspaceContext(WorkspaceId(workspaceId), UserId(ownerId))
    }

    companion object {
        const val HEADER = "X-Workspace-Id"
    }
}
