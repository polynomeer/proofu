package com.proofu.renderer

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.UserId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.identity.Profile
import com.proofu.domain.identity.ProfileLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RendererTest {
    private val output =
        GeneratedOutput(
            listOf(
                GeneratedBlock(
                    "motivation-1",
                    "SaaS 제품을 만들고 싶습니다.",
                    certainty = Certainty.UNSUPPORTED,
                    approvedByUser = true,
                ),
                GeneratedBlock(
                    "experience-1",
                    "온보딩 재설계로 활성화율을 40% 개선했습니다.\n두 번째 줄",
                    claimRefs = setOf(ClaimId(UUID.randomUUID())),
                    certainty = Certainty.SUPPORTED,
                    warnings = listOf("내부 경고"),
                ),
                GeneratedBlock("experience-2", "승인되지 않은 문장", certainty = Certainty.INFERRED),
            ),
        )
    private val document =
        RenderableDocument.of(
            title = "자기소개서",
            type = DocumentType.COVER_LETTER,
            language = "ko",
            company = "예시 주식회사",
            roleTitle = "프로덕트 매니저",
            template = DocumentTemplate.latest(DocumentType.COVER_LETTER),
            output = output,
        )

    @Test
    fun `renderable document keeps only exportable blocks under their template sections`() {
        assertThat(document.sections.map { it.title }).containsExactly("지원 동기", "관련 경험")
        assertThat(document.paragraphs).hasSize(2).doesNotContain("승인되지 않은 문장")
    }

    @Test
    fun `every implemented format renders real text that validates and carries no internals`() {
        ExportFormat.entries.filter { it.implemented }.forEach { format ->
            val renderer = Renderers.forFormat(format)!!
            val rendered = renderer.render(document)
            assertThat(rendered.bytes).isNotEmpty()
            assertThat(ExportValidator.validate(rendered, document)).describedAs(format.name).isEmpty()
            val text = ExportValidator.extractText(rendered)
            assertThat(text).contains("40% 개선").contains("예시 주식회사")
            assertThat(text).doesNotContain("experience-1").doesNotContain("SUPPORTED").doesNotContain("내부 경고")
        }
    }

    @Test
    fun `pdf embeds the korean font and reports its page count`() {
        val rendered = PdfRenderer().render(document)
        assertThat(rendered.pageCount).isEqualTo(1)
        assertThat(String(rendered.bytes, Charsets.ISO_8859_1)).contains("NotoSansKR")
        assertThat(ExportValidator.extractText(rendered)).contains("자기소개서").contains("40% 개선")
    }

    @Test
    fun `validation reports missing or reordered paragraphs`() {
        val rendered = MarkdownRenderer().render(document)
        val tampered = document.copy(sections = document.sections.reversed())
        assertThat(ExportValidator.validate(rendered, tampered)).anyMatch { it.startsWith("out of order") }
        val extra = document.copy(sections = document.sections + RenderableSection("기여 계획", listOf("없는 문장")))
        assertThat(ExportValidator.validate(rendered, extra)).anyMatch { it.startsWith("missing") }
    }

    @Test
    fun `a profile becomes a parseable header in every format, before the title`() {
        val profile =
            Profile(
                UserId(UUID.randomUUID()),
                "홍길동",
                headline = "B2B SaaS 프로덕트 매니저",
                email = "hong@example.com",
                phone = "010-1234-5678",
                links = listOf(ProfileLink("GitHub", "https://github.com/hong")),
            )
        val withHeader = document.copy(contact = RenderableContact.of(profile))
        assertThat(withHeader.expectedText.take(4))
            .containsExactly(
                "홍길동",
                "B2B SaaS 프로덕트 매니저",
                "hong@example.com · 010-1234-5678",
                "GitHub: https://github.com/hong",
            )
        ExportFormat.entries.forEach { format ->
            val rendered = Renderers.forFormat(format)!!.render(withHeader)
            assertThat(ExportValidator.validate(rendered, withHeader)).describedAs(format.name).isEmpty()
            val text = ExportValidator.extractText(rendered)
            assertThat(text.indexOf("홍길동")).isLessThan(text.indexOf("자기소개서"))
        }
    }
}
