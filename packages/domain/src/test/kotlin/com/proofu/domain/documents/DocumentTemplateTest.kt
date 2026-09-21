package com.proofu.domain.documents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentTemplateTest {
    @Test
    fun `every document type has a ko-v1 template with stable block ids`() {
        DocumentType.entries.forEach { type ->
            val template = DocumentTemplate.latest(type)
            assertThat(template.version).isEqualTo(DocumentTemplate.KO_V1)
            val first = template.sections.first()
            assertThat(template.sectionOf(first.blockId(3))).isEqualTo(first)
        }
    }

    @Test
    fun `english documents get en-v1 with the same section ids as ko-v1`() {
        DocumentType.entries.forEach { type ->
            val ko = DocumentTemplate.latest(type, "ko")
            val en = DocumentTemplate.latest(type, "en-US")
            assertThat(en.version).isEqualTo(DocumentTemplate.EN_V1)
            assertThat(en.language).isEqualTo("en")
            assertThat(en.sections.map { it.id }).isEqualTo(ko.sections.map { it.id })
            assertThat(en.sections.map { it.title }).noneMatch { t -> t.any { it in '가'..'힣' } }
        }
        assertThat(DocumentTemplate.latest(DocumentType.RESUME, "fr").version).isEqualTo(DocumentTemplate.KO_V1)
    }

    @Test
    fun `unknown versions resolve to nothing`() {
        assertThat(DocumentTemplate.find("en-v9", DocumentType.RESUME)).isNull()
    }
}
