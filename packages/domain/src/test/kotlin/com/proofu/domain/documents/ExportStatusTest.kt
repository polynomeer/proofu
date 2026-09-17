package com.proofu.domain.documents

import com.proofu.domain.common.InvalidStatusTransition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ExportStatusTest {
    @Test
    fun `happy path runs requested to ready to expired`() {
        val ready =
            ExportStatus.REQUESTED
                .transitionTo(ExportStatus.RENDERING)
                .transitionTo(ExportStatus.VALIDATING)
                .transitionTo(ExportStatus.READY)
        assertThat(ready.transitionTo(ExportStatus.EXPIRED)).isEqualTo(ExportStatus.EXPIRED)
    }

    @Test
    fun `ready cannot go back to rendering`() {
        assertThatThrownBy { ExportStatus.READY.transitionTo(ExportStatus.RENDERING) }
            .isInstanceOf(InvalidStatusTransition::class.java)
    }
}
