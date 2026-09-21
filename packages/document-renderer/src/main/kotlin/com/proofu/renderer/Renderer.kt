package com.proofu.renderer

import com.proofu.domain.documents.ExportFormat

data class Rendered(
    val bytes: ByteArray,
    val format: ExportFormat,
    val rendererVersion: String,
    /** Known only for paginated formats. */
    val pageCount: Int?,
)

interface Renderer {
    val format: ExportFormat
    val version: String

    fun render(document: RenderableDocument): Rendered
}

/** The renderer for a format, or null while the format is not implemented (ADR-0009 §3). */
object Renderers {
    private val all: Map<ExportFormat, Renderer> =
        listOf(DocxRenderer(), MarkdownRenderer(), JsonRenderer()).associateBy { it.format }

    fun forFormat(format: ExportFormat): Renderer? = all[format]
}
