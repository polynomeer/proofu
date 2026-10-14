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
     * The web BFF's client id. An ID token sent to `POST /me/reauthentications` must be issued to
     * it (`aud`), ADR-0010 결정 3.
     */
    val clientId: String? = null,
    /** Sensitive actions require a login newer than this. */
    val reauthMaxAge: Duration = Duration.ofMinutes(10),
) {
    val oidc: Boolean get() = mode == "oidc"

    fun requireOidcConfigured() {
        check(!issuer.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_ISSUER" }
        check(!jwksUri.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_JWKS_URI" }
        check(!clientId.isNullOrBlank()) { "proofu.auth.mode=oidc needs OIDC_CLIENT_ID" }
    }
}
