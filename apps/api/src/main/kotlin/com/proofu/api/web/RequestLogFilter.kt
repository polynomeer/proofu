package com.proofu.api.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

/**
 * One log line per request and one request id for the whole call
 * (docs/operations/monitoring.md). The URI is the route template, never the concrete path, so
 * ids stay out of the logs; query strings, bodies and tokens are never written.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLogFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RequestLogFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = acceptable(request.getHeader(HEADER)) ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, requestId)
        response.setHeader(HEADER, requestId)
        val startedAt = System.nanoTime()
        try {
            chain.doFilter(request, response)
        } finally {
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            log.info(
                "{} {} status={} durationMs={} requestId={}",
                request.method,
                route(request),
                response.status,
                durationMs,
                requestId,
            )
            MDC.remove(MDC_KEY)
        }
    }

    /** Health checks and metric scrapes would drown the real traffic. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith("/actuator")

    /** The matched route ("/api/v1/skills/{id}"), falling back to the raw path for 404s. */
    private fun route(request: HttpServletRequest): String =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: request.requestURI

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"

        private val ALLOWED = Regex("^[A-Za-z0-9._-]{1,64}$")

        /** A caller-supplied id is only reused when it is short and plain; otherwise we mint one. */
        fun acceptable(value: String?): String? = value?.takeIf { ALLOWED.matches(it) }

        /** The current request's id, for rows that record who asked for a change. */
        fun current(): String? = MDC.get(MDC_KEY)
    }
}
