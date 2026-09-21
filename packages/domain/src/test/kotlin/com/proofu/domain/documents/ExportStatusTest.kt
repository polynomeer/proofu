package com.proofu.domain.documents

import com.proofu.domain.common.InvalidStatusTransition
import com.proofu.domain.common.UnapprovedBlocksInExport
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

    @Test
    fun `export gate refuses unapproved blocks and keeps only exportable ones`() {
        val ok = GeneratedBlock("a-1", "x", certainty = Certainty.SUPPORTED)
        val approved = GeneratedBlock("a-2", "y", certainty = Certainty.INFERRED, approvedByUser = true)
        val pending = GeneratedBlock("a-3", "z", certainty = Certainty.UNSUPPORTED)

        assertThat(
            ExportGate.exportableBlocks(GeneratedOutput(listOf(ok, approved, pending))),
        ).containsExactly(ok, approved)
        assertThatThrownBy { ExportGate.requireExportable(GeneratedOutput(listOf(ok, pending))) }
            .isInstanceOf(UnapprovedBlocksInExport::class.java)
        assertThat(ExportGate.requireExportable(GeneratedOutput(listOf(ok, approved))).blocks).hasSize(2)
        assertThat(ExportFormat.entries).allMatch { it.implemented }
        assertThat(ExportFormat.DOCX.extension).isEqualTo("docx")
    }
}
