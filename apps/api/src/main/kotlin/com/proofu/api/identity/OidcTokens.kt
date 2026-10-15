package com.proofu.api.identity

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Instant

/**
 * A decoder that checks signature (the issuer's JWKS, fetched lazily so the API starts even if
 * the IdP is momentarily down), issuer, expiry and that `aud` contains one of [audiences]. Access
 * tokens use the API audience; ID tokens sent for step-up use our client ids (web, iOS).
 */
internal fun oidcJwtDecoder(
    issuer: String,
    jwksUri: String,
    audiences: Set<String>,
): JwtDecoder {
    require(audiences.isNotEmpty()) { "an empty audience set would accept nothing or, worse, be read as anything" }
    val decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
    val audienceCheck =
        OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.audience.orEmpty().any { it in audiences }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "aud must contain one of $audiences", null),
                )
            }
        }
    decoder.setJwtValidator(
        DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(issuer), audienceCheck),
    )
    return decoder
}

/**
 * When the user logged in. Only `auth_time` says that: `iat` is renewed on every refresh token
 * grant, so falling back to it would let a long-lived session pass the step-up check.
 */
internal fun Jwt.authTime(): Instant? =
    (getClaim<Any>("auth_time") as? Instant)
        ?: (getClaim<Any>("auth_time") as? Number)?.let { Instant.ofEpochSecond(it.toLong()) }
