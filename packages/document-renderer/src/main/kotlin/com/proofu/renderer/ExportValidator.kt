package com.proofu.renderer

import com.proofu.domain.documents.ExportFormat
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor
import java.io.ByteArrayInputStream

/**
 * Re-extracts text from the rendered file and checks that the title and every paragraph appear
 * in reading order (documents §ATS 호환성 "검증"). Returns the problems; empty means valid.
 */
object ExportValidator {
    fun validate(
        rendered: Rendered,
        document: RenderableDocument,
    ): List<String> {
        // Compared without whitespace: a PDF line can break inside a Korean word or after a
        // hyphen, and the extractor returns that break as a space the source never had
        // ("처리로" → "처리 로", "per-event" → "per- event"). Every other character must still be
        // there, in order.
        val text = compact(extractText(rendered))
        val problems = mutableListOf<String>()
        var cursor = 0
        document.expectedText.forEach { expected ->
            val needle = compact(expected)
            if (needle.isEmpty()) return@forEach
            val at = text.indexOf(needle, cursor)
            if (at < 0) {
                val preview = normalize(expected).take(40)
                problems += if (text.contains(needle)) "out of order: $preview" else "missing: $preview"
            } else {
                cursor = at + needle.length
            }
        }
        return problems
    }

    fun extractText(rendered: Rendered): String =
        when (rendered.format) {
            ExportFormat.DOCX ->
                XWPFDocument(ByteArrayInputStream(rendered.bytes)).use { doc ->
                    XWPFWordExtractor(doc).use { normalize(it.text) }
                }
            ExportFormat.MARKDOWN, ExportFormat.JSON -> normalize(String(rendered.bytes, Charsets.UTF_8))
            ExportFormat.PDF -> {
                val reader = PdfReader(rendered.bytes)
                try {
                    val extractor = PdfTextExtractor(reader)
                    normalize((1..reader.numberOfPages).joinToString(" ") { extractor.getTextFromPage(it) })
                } finally {
                    reader.close()
                }
            }
        }

    private fun compact(s: String): String = normalize(s).replace(" ", "")

    /** Whitespace-insensitive comparison; JSON escapes are undone so quoted text still matches. */
    private fun normalize(s: String): String =
        s
            .replace("\\n", " ")
            .replace("\\\"", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
}
