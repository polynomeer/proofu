package com.proofu.domain.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Uuid7IdGeneratorTest {
    private val generator = Uuid7IdGenerator()

    @Test
    fun `generates version 7 identifiers`() {
        assertThat(generator.next().version()).isEqualTo(7)
    }

    @Test
    fun `identifiers are unique and time ordered`() {
        val ids = List(1_000) { generator.next() }
        assertThat(ids.toSet()).hasSize(ids.size)
        assertThat(ids.map { it.toString() }).isSorted()
    }
}
