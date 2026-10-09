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
        val text = extractText(rendered)
        val problems = mutableListOf<String>()
        var cursor = 0
        document.expectedText.forEach { expected ->
            val needle = normalize(expected)
            if (needle.isEmpty()) return@forEach
            val at = text.indexOf(needle, cursor)
            if (at < 0) {
                problems +=
                    if (text.contains(needle)) "out of order: ${needle.take(40)}" else "missing: ${needle.take(40)}"
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

    /** Whitespace-insensitive comparison; JSON escapes are undone so quoted text still matches. */
    private fun normalize(s: String): String =
        s
            .replace("\\n", " ")
            .replace("\\\"", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
}
