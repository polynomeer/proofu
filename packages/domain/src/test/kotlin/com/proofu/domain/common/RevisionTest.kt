package com.proofu.domain.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RevisionTest {
    @Test
    fun `starts at one and increments`() {
        assertThat(Revision.INITIAL.next()).isEqualTo(Revision(2))
    }

    @Test
    fun `rejects non-positive values`() {
        assertThatThrownBy { Revision(0) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
