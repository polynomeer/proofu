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
    fun `unknown versions resolve to nothing`() {
        assertThat(DocumentTemplate.find("en-v9", DocumentType.RESUME)).isNull()
    }
}
