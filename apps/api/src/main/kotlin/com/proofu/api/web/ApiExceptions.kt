package com.proofu.api.web

/** Resource absent in the caller's workspace. Also used for resources owned by another workspace. */
class ResourceNotFoundException(
    resource: String,
    id: Any,
) : RuntimeException("$resource $id not found")

/** Optimistic locking failure: the client's revision/version is older than the stored one. */
class StaleVersionException(
    resource: String,
    expected: Long,
    actual: Long,
) : RuntimeException("$resource has revision $actual, request carried $expected")

/** Malformed client input outside bean validation, e.g. an undecodable cursor or a source-dependent field. */
class InvalidRequestException(
    message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) : RuntimeException(message)

/** A contract path whose integration does not exist yet (URL fetching, career-ops, iterview). */
class NotImplementedException(
    feature: String,
) : RuntimeException("$feature is not available yet")
