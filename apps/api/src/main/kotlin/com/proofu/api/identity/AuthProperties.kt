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
    /** Sensitive actions require an `auth_time` newer than this. */
    val reauthMaxAge: Duration = Duration.ofMinutes(10),
) {
    val oidc: Boolean get() = mode == "oidc"

    fun requireOidcConfigured() {
        check(!issuer.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_ISSUER" }
        check(!jwksUri.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_JWKS_URI" }
    }
}
