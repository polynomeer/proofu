package com.proofu.renderer

import com.proofu.domain.documents.ExportFormat
import org.apache.poi.xwpf.usermodel.Borders
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.ByteArrayOutputStream

/**
 * Single-column, heading-hierarchy DOCX (documents §ATS 호환성): real text, no tables, no text
 * boxes, body 11pt. Fonts are named, not embedded; the reader supplies the Korean glyphs.
 */
class DocxRenderer : Renderer {
    override val format = ExportFormat.DOCX
    override val version = VERSION

    override fun render(document: RenderableDocument): Rendered {
        XWPFDocument().use { doc ->
            document.contact?.let { contact ->
                doc.createParagraph().apply {
                    spacingAfter = 40
                    run(contact.name, size = TITLE_PT, bold = true)
                }
                contact.headline?.let {
                    doc.createParagraph().apply {
                        spacingAfter = 40
                        run(it, size = META_PT, color = "555555")
                    }
                }
                contact.contactLine?.let {
                    doc.createParagraph().apply {
                        spacingAfter = 40
                        run(it, size = META_PT)
                    }
                }
                contact.links.forEach {
                    doc.createParagraph().apply {
                        spacingAfter = 40
                        run(it, size = META_PT)
                    }
                }
                doc.createParagraph().apply {
                    spacingAfter = 160
                    borderBottom = Borders.SINGLE
                }
            }
            doc.createParagraph().apply {
                alignment = ParagraphAlignment.LEFT
                spacingAfter = 120
                run(document.title, size = if (document.contact == null) TITLE_PT else HEADING_PT, bold = true)
            }
            doc.createParagraph().apply {
                spacingAfter = 240
                run("${document.company} · ${document.roleTitle}", size = META_PT, color = "555555")
            }
            document.sections.forEach { section ->
                doc.createParagraph().apply {
                    spacingBefore = 240
                    spacingAfter = 80
                    run(section.title, size = HEADING_PT, bold = true)
                }
                section.paragraphs.forEach { text ->
                    doc.createParagraph().apply {
                        spacingAfter = 120
                        run(text, size = BODY_PT)
                    }
                }
            }
            val out = ByteArrayOutputStream()
            doc.write(out)
            return Rendered(out.toByteArray(), format, version, pageCount = null)
        }
    }

    private fun XWPFParagraph.run(
        text: String,
        size: Int,
        bold: Boolean = false,
        color: String? = null,
    ) {
        val lines = text.split('\n')
        val run = createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
        if (color != null) run.color = color
        lines.forEachIndexed { i, line ->
            if (i > 0) run.addBreak()
            run.setText(line, i)
        }
    }

    companion object {
        const val VERSION = "docx-poi-2"
        const val FONT = "Malgun Gothic"
        const val TITLE_PT = 18
        const val HEADING_PT = 13
        const val META_PT = 10
        const val BODY_PT = 11
    }
}
