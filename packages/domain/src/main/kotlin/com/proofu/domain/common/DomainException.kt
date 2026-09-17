package com.proofu.domain.common

sealed class DomainException(
    message: String,
) : RuntimeException(message)

class DomainRuleViolation(
    message: String,
) : DomainException(message)

class WorkspaceBoundaryViolation(
    message: String,
) : DomainException(message)

class InvalidStatusTransition(
    from: Any,
    to: Any,
) : DomainException("transition $from -> $to is not allowed")

class ImmutableSnapshotViolation(
    message: String,
) : DomainException(message)

/** Throws [DomainRuleViolation] when [condition] is false. */
inline fun domainRequire(
    condition: Boolean,
    lazyMessage: () -> String,
) {
    if (!condition) throw DomainRuleViolation(lazyMessage())
}
