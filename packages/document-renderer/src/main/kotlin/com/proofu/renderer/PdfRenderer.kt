package com.proofu.renderer

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfWriter
import com.proofu.domain.documents.ExportFormat
import java.awt.Color
import java.io.ByteArrayOutputStream

/**
 * Single-column A4 PDF with Noto Sans KR embedded (OFL, bundled under resources/fonts), so
 * Korean glyphs never depend on the reader's machine. Same content model as the DOCX.
 */
class PdfRenderer : Renderer {
    override val format = ExportFormat.PDF
    override val version = VERSION

    override fun render(document: RenderableDocument): Rendered {
        val out = ByteArrayOutputStream()
        val pdf = Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN)
        val writer = PdfWriter.getInstance(pdf, out)
        pdf.addTitle(document.title)
        pdf.addCreator("ProofU $VERSION")
        pdf.open()
        document.contact?.let { contact ->
            pdf.add(paragraph(contact.name, Fonts.bold(TITLE_PT), after = 2f))
            contact.headline?.let {
                pdf.add(
                    paragraph(it, Fonts.regular(META_PT, Color(0x55, 0x55, 0x55)), after = 2f),
                )
            }
            contact.contactLine?.let { pdf.add(paragraph(it, Fonts.regular(META_PT), after = 2f)) }
            contact.links.forEach { pdf.add(paragraph(it, Fonts.regular(META_PT), after = 2f)) }
            pdf.add(paragraph(" ", Fonts.regular(META_PT), after = 10f))
        }
        pdf.add(
            paragraph(
                document.title,
                Fonts.bold(
                    if (document.contact ==
                        null
                    ) {
                        TITLE_PT
                    } else {
                        HEADING_PT
                    },
                ),
                after = 6f,
            ),
        )
        pdf.add(
            paragraph(
                "${document.company} · ${document.roleTitle}",
                Fonts.regular(META_PT, Color(0x55, 0x55, 0x55)),
                after = 14f,
            ),
        )
        document.sections.forEach { section ->
            pdf.add(paragraph(section.title, Fonts.bold(HEADING_PT), before = 12f, after = 4f))
            section.paragraphs.forEach { text -> pdf.add(paragraph(text, Fonts.regular(BODY_PT), after = 6f)) }
        }
        val pages = writer.pageNumber // the page being written when the document closes
        pdf.close()
        return Rendered(out.toByteArray(), format, version, pageCount = pages)
    }

    private fun paragraph(
        text: String,
        font: Font,
        before: Float = 0f,
        after: Float = 0f,
    ): Paragraph =
        Paragraph(text, font).apply {
            alignment = Element.ALIGN_LEFT
            leading = font.size * LINE_HEIGHT
            spacingBefore = before
            spacingAfter = after
        }

    /** Base fonts are parsed once; OpenPDF embeds only the glyphs a document uses. */
    private object Fonts {
        private val regularBase = load("NotoSansKR-Regular.ttf")
        private val boldBase = load("NotoSansKR-Bold.ttf")

        fun regular(
            size: Int,
            color: Color = Color.BLACK,
        ) = Font(regularBase, size.toFloat(), Font.NORMAL, color)

        fun bold(size: Int) = Font(boldBase, size.toFloat(), Font.NORMAL, Color.BLACK)

        private fun load(name: String): BaseFont {
            val bytes =
                checkNotNull(PdfRenderer::class.java.getResourceAsStream("/fonts/$name")) { "font $name missing" }
                    .use { it.readBytes() }
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null)
        }
    }

    companion object {
        const val VERSION = "pdf-openpdf-2"
        const val TITLE_PT = 18
        const val HEADING_PT = 13
        const val META_PT = 10
        const val BODY_PT = 11
        private const val MARGIN = 56.7f // 20 mm
        private const val LINE_HEIGHT = 1.5f
    }
}
