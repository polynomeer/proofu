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

/** Malformed client input outside bean validation, e.g. an undecodable pagination cursor. */
class InvalidRequestException(
    message: String,
) : RuntimeException(message)
