package com.proofu.renderer

import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.ExportGate
import com.proofu.domain.documents.GeneratedOutput

data class RenderableSection(
    val title: String,
    val paragraphs: List<String>,
)

/** What a file may carry: titles and paragraphs only. Ids, certainty and warnings never reach a file. */
data class RenderableDocument(
    val title: String,
    val type: DocumentType,
    val language: String,
    val company: String,
    val roleTitle: String,
    val templateVersion: String,
    val sections: List<RenderableSection>,
) {
    /** Every paragraph in reading order; validators compare extracted text against this. */
    val paragraphs: List<String> get() = sections.flatMap { it.paragraphs }

    companion object {
        /** Blocks grouped by template section in template order; empty sections are left out. */
        fun of(
            title: String,
            type: DocumentType,
            language: String,
            company: String,
            roleTitle: String,
            template: DocumentTemplate,
            output: GeneratedOutput,
        ): RenderableDocument {
            val blocks = ExportGate.exportableBlocks(output)
            val sections =
                template.sections.mapNotNull { section ->
                    val own =
                        blocks
                            .filter {
                                template.sectionOf(
                                    it.blockId,
                                ) == section
                            }.map { it.text.trim() }
                            .filter { it.isNotEmpty() }
                    if (own.isEmpty()) null else RenderableSection(section.title, own)
                }
            return RenderableDocument(title.trim(), type, language, company, roleTitle, template.version, sections)
        }
    }
}
