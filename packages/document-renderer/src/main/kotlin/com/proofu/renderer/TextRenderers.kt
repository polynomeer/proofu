package com.proofu.renderer

import com.proofu.domain.documents.ExportFormat
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

class MarkdownRenderer : Renderer {
    override val format = ExportFormat.MARKDOWN
    override val version = "md-1"

    override fun render(document: RenderableDocument): Rendered {
        val text =
            buildString {
                appendLine("# ${document.title}")
                appendLine()
                appendLine("${document.company} · ${document.roleTitle}")
                document.sections.forEach { section ->
                    appendLine()
                    appendLine("## ${section.title}")
                    section.paragraphs.forEach { p ->
                        appendLine()
                        appendLine(p)
                    }
                }
            }
        return Rendered(text.toByteArray(Charsets.UTF_8), format, version, pageCount = null)
    }
}

/** Structured export: the same titles and paragraphs, nothing the DOCX would not show. */
class JsonRenderer(
    private val mapper: ObjectMapper = JsonMapper.builder().build(),
) : Renderer {
    override val format = ExportFormat.JSON
    override val version = "json-1"

    override fun render(document: RenderableDocument): Rendered {
        val body =
            mapOf(
                "title" to document.title,
                "type" to document.type.name,
                "language" to document.language,
                "company" to document.company,
                "roleTitle" to document.roleTitle,
                "templateVersion" to document.templateVersion,
                "sections" to document.sections.map { mapOf("title" to it.title, "paragraphs" to it.paragraphs) },
            )
        return Rendered(mapper.writeValueAsBytes(body), format, version, pageCount = null)
    }
}
