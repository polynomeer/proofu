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
import org.springframework.security.oauth2.jwt.JwtDecoder
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

    private fun jwtDecoder(): JwtDecoder =
        oidcJwtDecoder(checkNotNull(auth.issuer), checkNotNull(auth.jwksUri), setOf(auth.audience))

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
                // The scrape carries no personal data and Prometheus cannot hold a token; it is
                // kept off the public port instead (docs/operations/monitoring.md §스크랩 경로 보호).
                "/actuator/prometheus",
                "/api/v1/openapi.json",
                "/api/v1/docs/**",
            )
    }
}
