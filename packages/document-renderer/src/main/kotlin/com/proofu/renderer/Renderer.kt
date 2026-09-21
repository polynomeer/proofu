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

/** The renderer for a format, or null for a format without one. */
object Renderers {
    private val all: Map<ExportFormat, Renderer> =
        listOf(DocxRenderer(), PdfRenderer(), MarkdownRenderer(), JsonRenderer()).associateBy { it.format }

    fun forFormat(format: ExportFormat): Renderer? = all[format]
}
