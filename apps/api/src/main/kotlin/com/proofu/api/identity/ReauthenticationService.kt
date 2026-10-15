package com.proofu.api.identity

import com.proofu.api.audit.AuditLog
import com.proofu.domain.common.DomainRuleViolation
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * ADR-0010 결정 3: hosted IdPs put no `auth_time` in access tokens, so the web sends the ID token
 * it got at login and the API checks it itself — signature against the issuer's JWKS, `aud` = one
 * of our clients (the web BFF, or the iOS app per ADR-0011 — it sends its own the same way), the caller's own `(iss, sub)`, and an `auth_time` inside the step-up window — then
 * keeps that time in `users.reauthenticated_at`. The web vouches for nothing.
 */
@Service
class ReauthenticationService(
    private val auth: AuthProperties,
    private val jdbc: JdbcTemplate,
    private val audit: AuditLog,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ReauthenticationService::class.java)

    private val idTokens: JwtDecoder by lazy {
        oidcJwtDecoder(checkNotNull(auth.issuer), checkNotNull(auth.jwksUri), auth.clients)
    }

    @Transactional
    fun record(
        workspace: WorkspaceContext,
        idToken: String,
    ) {
        if (!auth.oidc) throw DomainRuleViolation("the header auth mode has no login to prove")
        val jwt =
            try {
                idTokens.decode(idToken)
            } catch (e: JwtException) {
                // The reason is safe to log; the token itself never is.
                log.info("Refused a re-authentication ID token: {}", e.javaClass.simpleName)
                throw ReauthenticationRequiredException(auth.reauthMaxAge)
            }
        val (issuer, subject) =
            jdbc.queryForObject(
                "select oidc_issuer, oidc_subject from users where id = ?",
                { rs, _ -> rs.getString("oidc_issuer") to rs.getString("oidc_subject") },
                workspace.userId.value,
            )
        val at = jwt.authTime()
        if (jwt.issuer?.toString() != issuer ||
            jwt.subject != subject ||
            at == null ||
            at.plus(auth.reauthMaxAge).isBefore(Instant.now(clock))
        ) {
            throw ReauthenticationRequiredException(auth.reauthMaxAge)
        }
        // A replayed older token never moves the time backwards.
        jdbc.update(
            "update users set reauthenticated_at = greatest(coalesce(reauthenticated_at, ?), ?) where id = ?",
            java.sql.Timestamp.from(at),
            java.sql.Timestamp.from(at),
            workspace.userId.value,
        )
        audit.record(workspace, "user.reauthenticated", "user", workspace.userId.value)
    }
}
