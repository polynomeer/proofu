package com.proofu.api.identity

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `proofu.auth.mode`: `header` (interim `X-Workspace-Id`, never in production) or `oidc`
 * (ADR-0010: bearer JWT from the web BFF, verified against the issuer's JWKS).
 */
@ConfigurationProperties(prefix = "proofu.auth")
data class AuthProperties(
    val mode: String = "header",
    val issuer: String? = null,
    val jwksUri: String? = null,
    /** The token's `aud` must contain this; the IdP client is configured to add it. */
    val audience: String = "proofu-api",
    /**
     * Our own OIDC clients: the web BFF and, from ADR-0011, the iOS app (`OIDC_CLIENT_ID`, comma
     * separated). An ID token sent to `POST /me/reauthentications` must be issued to one of them
     * (`aud`), ADR-0010 결정 3.
     */
    val clientIds: List<String> = emptyList(),
    /** Sensitive actions require a login newer than this. */
    val reauthMaxAge: Duration = Duration.ofMinutes(10),
) {
    val oidc: Boolean get() = mode == "oidc"

    fun requireOidcConfigured() {
        check(!issuer.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_ISSUER" }
        check(!jwksUri.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_JWKS_URI" }
        check(clients.isNotEmpty()) { "proofu.auth.mode=oidc needs OIDC_CLIENT_ID" }
    }

    /** [clientIds] without blanks, so `OIDC_CLIENT_ID=""` or `"proofu-web, "` cannot widen anything. */
    val clients: Set<String> get() = clientIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
