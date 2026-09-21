package com.proofu.renderer

import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.ExportGate
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.identity.Profile

/** Document header from the user's profile: what an ATS parses for identity and contact. */
data class RenderableContact(
    val name: String,
    val headline: String?,
    val contactLine: String?,
    val links: List<String>,
) {
    /** Lines in reading order, for validators. */
    val lines: List<String> get() = listOfNotNull(name, headline, contactLine) + links

    companion object {
        fun of(profile: Profile) =
            RenderableContact(
                name = profile.fullName,
                headline = profile.headline?.ifBlank { null },
                contactLine = profile.contactLine.ifBlank { null },
                links = profile.links.map { "${it.label}: ${it.url}" },
            )
    }
}

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
    val contact: RenderableContact? = null,
) {
    /** Every paragraph in reading order; validators compare extracted text against this. */
    val paragraphs: List<String> get() = sections.flatMap { it.paragraphs }

    /** Everything a file must contain, in reading order: header, title, section titles, paragraphs. */
    val expectedText: List<String>
        get() = contact?.lines.orEmpty() + listOf(title) + sections.flatMap { listOf(it.title) + it.paragraphs }

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
            profile: Profile? = null,
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
            return RenderableDocument(
                title.trim(),
                type,
                language,
                company,
                roleTitle,
                template.version,
                sections,
                profile?.let(RenderableContact::of),
            )
        }
    }
}
