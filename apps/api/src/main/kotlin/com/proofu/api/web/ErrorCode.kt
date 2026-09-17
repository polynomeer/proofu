package com.proofu.api.web

import org.springframework.http.HttpStatus

/** Stable machine-readable codes carried in the Problem Details `code` property (api/conventions.md). */
enum class ErrorCode(
    val status: HttpStatus,
) {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    REAUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN_WORKSPACE(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT_STALE_VERSION(HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    SNAPSHOT_IMMUTABLE(HttpStatus.CONFLICT),
    DOMAIN_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT),
    UNSUPPORTED_CLAIM_IN_EXPORT(HttpStatus.UNPROCESSABLE_CONTENT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
}
