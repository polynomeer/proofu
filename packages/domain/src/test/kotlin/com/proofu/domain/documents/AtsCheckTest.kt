package com.proofu.domain.documents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AtsCheckTest {
    private val template = DocumentTemplate.latest(DocumentType.RESUME)

    private fun block(
        id: String,
        text: String,
        approved: Boolean = true,
    ) = GeneratedBlock(id, text, certainty = Certainty.UNSUPPORTED, approvedByUser = approved)

    @Test
    fun `reports facts per check without a score`() {
        val output =
            GeneratedOutput(
                listOf(
                    block("summary-1", "SaaS 제품 기획 5년. 연락처 pm@example.com / 010-1234-5678"),
                    block("career-1", "2024.03 – 현재 예시 주식회사 PM. 2022년 1월 입사."),
                    block("skills-1", "이 문장은 승인되지 않아 파일에 들어가지 않습니다", approved = false),
                ),
            )

        val report =
            AtsChecker.check(
                template,
                output,
                approvedRequirements = listOf("SaaS 제품 기획 경험", "Kotlin 백엔드"),
                readyFormats = setOf(ExportFormat.MARKDOWN),
                pageCount = null,
            )

        val byCode = report.findings.associateBy { it.code }
        assertThat(byCode.getValue("SECTION_STRUCTURE").severity).isEqualTo(AtsSeverity.WARN)
        assertThat(byCode.getValue("SECTION_STRUCTURE").details).contains("핵심 역량", "기술", "학력", "프로젝트")
        assertThat(byCode.getValue("KEYWORDS").severity).isEqualTo(AtsSeverity.WARN)
        assertThat(byCode.getValue("KEYWORDS").details).containsExactly("Kotlin 백엔드")
        assertThat(byCode.getValue("CONTACT").severity).isEqualTo(AtsSeverity.PASS)
        assertThat(byCode.getValue("DATE_FORMAT").severity).isEqualTo(AtsSeverity.INFO)
        assertThat(byCode.getValue("LAYOUT").severity).isEqualTo(AtsSeverity.PASS)
        assertThat(byCode.getValue("FILE_FORMAT").severity).isEqualTo(AtsSeverity.INFO)
        assertThat(byCode.getValue("LENGTH").severity).isEqualTo(AtsSeverity.PASS)
        assertThat(report.warnings).isEqualTo(2)
        assertThat(report.findings.map { it.message }).noneMatch { it.contains("합격") }
    }

    @Test
    fun `passes when every section is filled, keywords present, contact given and a submittable file exists`() {
        val output =
            GeneratedOutput(
                template.sections.map { s ->
                    block(s.blockId(1), "${s.title}: SaaS 제품 기획 경험 Kotlin. 010-1234-5678 pm@example.com")
                },
            )
        val report =
            AtsChecker.check(
                template,
                output,
                listOf("SaaS 제품 기획 경험", "Kotlin"),
                setOf(ExportFormat.PDF, ExportFormat.JSON),
                pageCount = 3,
            )
        assertThat(report.findings.filter { it.code != "LENGTH" }).allMatch { it.severity == AtsSeverity.PASS }
        assertThat(report.findings.single { it.code == "LENGTH" }.severity).isEqualTo(AtsSeverity.INFO)
        assertThat(report.findings.single { it.code == "FILE_FORMAT" }.details).containsExactly("PDF")
    }

    @Test
    fun `contact may come from the profile header instead of the body`() {
        val output = GeneratedOutput(listOf(block("summary-1", "연락처 없는 본문")))
        val report =
            AtsChecker.check(
                template,
                output,
                emptyList(),
                emptySet(),
                headerText = "hong@example.com · 010-1234-5678",
            )
        assertThat(report.findings.single { it.code == "CONTACT" }.severity).isEqualTo(AtsSeverity.PASS)
    }
}
