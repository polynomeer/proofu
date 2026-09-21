package com.proofu.domain.documents

import com.proofu.domain.common.InvalidStatusTransition
import com.proofu.domain.common.UnapprovedBlocksInExport

enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    /** PDF waits for the font bundle (ADR-0009 §3). */
    val implemented: Boolean,
) {
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", true),
    PDF("application/pdf", "pdf", false),
    MARKDOWN("text/markdown; charset=utf-8", "md", true),
    JSON("application/json", "json", true),
}

/** Export gate (ADR-0009 §6): the API checks it when accepting, the worker again before rendering. */
object ExportGate {
    fun requireExportable(output: GeneratedOutput): GeneratedOutput {
        val pending = output.blocksPendingApproval()
        if (pending.isNotEmpty()) throw UnapprovedBlocksInExport(pending.map { it.blockId })
        return output
    }

    /** Only the exportable blocks, in document order; a file never carries what the user did not approve. */
    fun exportableBlocks(output: GeneratedOutput): List<GeneratedBlock> = output.blocks.filter { it.exportable }
}

/** REQUESTED -> RENDERING -> VALIDATING -> READY | FAILED | EXPIRED */
enum class ExportStatus {
    REQUESTED,
    RENDERING,
    VALIDATING,
    READY,
    FAILED,
    EXPIRED,
    ;

    val allowedTransitions: Set<ExportStatus>
        get() =
            when (this) {
                REQUESTED -> setOf(RENDERING, FAILED)
                RENDERING -> setOf(VALIDATING, FAILED)
                VALIDATING -> setOf(READY, FAILED)
                READY -> setOf(EXPIRED)
                FAILED, EXPIRED -> emptySet()
            }

    fun transitionTo(target: ExportStatus): ExportStatus =
        if (target in allowedTransitions) target else throw InvalidStatusTransition(this, target)
}
