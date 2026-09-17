package com.proofu.domain.common

/**
 * Monotonic revision of a mutable source record. Provenance links pin a specific
 * revision so a document can be reproduced after the source changes.
 */
@JvmInline
value class Revision(
    val value: Long,
) : Comparable<Revision> {
    init {
        require(value >= 1) { "revision must be >= 1, was $value" }
    }

    fun next(): Revision = Revision(value + 1)

    override fun compareTo(other: Revision): Int = value.compareTo(other.value)

    companion object {
        val INITIAL = Revision(1)
    }
}
