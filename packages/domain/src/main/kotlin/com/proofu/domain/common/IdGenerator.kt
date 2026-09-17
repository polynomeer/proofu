package com.proofu.domain.common

import com.fasterxml.uuid.Generators
import java.util.UUID

/** Injected so tests can produce deterministic identifiers. */
fun interface IdGenerator {
    fun next(): UUID
}

/** UUIDv7: 48-bit Unix epoch millis + random. Sorts by creation time. */
class Uuid7IdGenerator : IdGenerator {
    private val generator = Generators.timeBasedEpochGenerator()

    override fun next(): UUID = generator.generate()
}
