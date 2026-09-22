package com.proofu.api.identity

import com.proofu.api.web.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

/**
 * ADR-0010 결정 1 §2: the API is an OIDC resource server. In `header` mode (local/test only)
 * the filter chain lets everything through and [HeaderWorkspaceResolver] does the work.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties::class)
class SecurityConfig(
    private val auth: AuthProperties,
    private val mapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            if (auth.oidc) {
                auth.requireOidcConfigured()
                authorizeHttpRequests {
                    PUBLIC_PATHS.forEach { authorize(it, permitAll) }
                    authorize(anyRequest, authenticated)
                }
                oauth2ResourceServer {
                    jwt { jwtDecoder = jwtDecoder() }
                    authenticationEntryPoint = problemEntryPoint()
                }
                exceptionHandling { authenticationEntryPoint = problemEntryPoint() }
            } else {
                authorizeHttpRequests { authorize(anyRequest, permitAll) }
            }
        }
        return http.build()
    }

    /** JWKS is fetched lazily on the first token, so the API starts even if the IdP is momentarily down. */
    private fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(checkNotNull(auth.jwksUri)).build()
        val audience =
            OAuth2TokenValidator<Jwt> { jwt ->
                if (jwt.audience?.contains(auth.audience) == true) {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(
                        OAuth2Error("invalid_token", "aud must contain ${auth.audience}", null),
                    )
                }
            }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(checkNotNull(auth.issuer)), audience),
        )
        return decoder
    }

    /** 401 as Problem Details with `code`, like every other API error. */
    private fun problemEntryPoint() =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _: AuthenticationException ->
            response.status = 401
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.setHeader("WWW-Authenticate", "Bearer")
            mapper.writeValue(
                response.outputStream,
                mapOf(
                    "type" to "https://proofu.dev/problems/unauthenticated",
                    "title" to "Unauthorized",
                    "status" to 401,
                    "detail" to "Authentication required",
                    "code" to ErrorCode.UNAUTHENTICATED.name,
                ),
            )
        }

    companion object {
        val PUBLIC_PATHS =
            listOf(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/api/v1/openapi.json",
                "/api/v1/docs/**",
            )
    }
}
