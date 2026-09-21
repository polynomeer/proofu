package com.proofu.domain.documents

import com.proofu.domain.matching.MatchFeatureCalculator

enum class AtsSeverity {
    PASS,
    INFO,
    WARN,
}

data class AtsFinding(
    val code: String,
    val severity: AtsSeverity,
    val message: String,
    val details: List<String> = emptyList(),
)

data class AtsReport(
    val findings: List<AtsFinding>,
) {
    val warnings: Int get() = findings.count { it.severity == AtsSeverity.WARN }
}

/**
 * Deterministic ATS compatibility checks (documents §ATS 호환성). Every finding is a fact about
 * the document; there is no score and nothing here predicts an outcome.
 */
object AtsChecker {
    fun check(
        template: DocumentTemplate,
        output: GeneratedOutput,
        approvedRequirements: List<String>,
        readyFormats: Set<ExportFormat>,
        pageCount: Int? = null,
        /** The profile header exports carry (Profile.contactLine); contact parsing looks there first. */
        headerText: String = "",
    ): AtsReport {
        val blocks = ExportGate.exportableBlocks(output)
        val text = blocks.joinToString("\n") { it.text }
        return AtsReport(
            listOf(
                sectionStructure(template, blocks),
                keywords(approvedRequirements, text),
                contact(headerText + "\n" + text),
                dateFormat(text),
                AtsFinding("LAYOUT", AtsSeverity.PASS, "단일 열 레이아웃이며 표·이미지·텍스트 상자를 쓰지 않습니다 (렌더러 보장)."),
                fileFormat(readyFormats),
                length(template.type, text, pageCount),
            ),
        )
    }

    private fun sectionStructure(
        template: DocumentTemplate,
        blocks: List<GeneratedBlock>,
    ): AtsFinding {
        val missing =
            template.sections
                .filter { s ->
                    blocks.none { template.sectionOf(it.blockId) == s }
                }.map { it.title }
        return if (missing.isEmpty()) {
            AtsFinding("SECTION_STRUCTURE", AtsSeverity.PASS, "템플릿의 모든 섹션에 문장이 있습니다.")
        } else {
            AtsFinding("SECTION_STRUCTURE", AtsSeverity.WARN, "비어 있는 섹션이 ${missing.size}개 있습니다.", missing)
        }
    }

    /** A requirement counts as missing when fewer than half of its content tokens appear anywhere. */
    private fun keywords(
        requirements: List<String>,
        text: String,
    ): AtsFinding {
        if (requirements.isEmpty()) {
            return AtsFinding("KEYWORDS", AtsSeverity.INFO, "승인된 요구사항이 없어 키워드를 비교하지 않았습니다.")
        }
        val missing = requirements.filter { MatchFeatureCalculator.coverage(it, text) < KEYWORD_THRESHOLD }
        return if (missing.isEmpty()) {
            AtsFinding("KEYWORDS", AtsSeverity.PASS, "승인된 요구사항 ${requirements.size}개의 핵심 단어가 모두 문서에 있습니다.")
        } else {
            AtsFinding("KEYWORDS", AtsSeverity.WARN, "핵심 단어가 문서에 없는 요구사항이 ${missing.size}개 있습니다.", missing)
        }
    }

    private fun contact(text: String): AtsFinding {
        val email = EMAIL.containsMatchIn(text)
        val phone = PHONE.containsMatchIn(text)
        return when {
            email && phone -> AtsFinding("CONTACT", AtsSeverity.PASS, "이메일과 전화번호를 찾았습니다.")
            email -> AtsFinding("CONTACT", AtsSeverity.WARN, "전화번호를 찾지 못했습니다.")
            phone -> AtsFinding("CONTACT", AtsSeverity.WARN, "이메일을 찾지 못했습니다.")
            else -> AtsFinding("CONTACT", AtsSeverity.WARN, "이메일과 전화번호를 찾지 못했습니다. 설정의 프로필에 연락처를 입력하세요.")
        }
    }

    private fun dateFormat(text: String): AtsFinding {
        val styles = DATE_STYLES.filter { (_, re) -> re.containsMatchIn(text) }.map { it.first }
        return when {
            styles.size <= 1 -> AtsFinding("DATE_FORMAT", AtsSeverity.PASS, "날짜 표기가 일관됩니다.")
            else -> AtsFinding("DATE_FORMAT", AtsSeverity.INFO, "날짜 표기가 ${styles.size}가지로 섞여 있습니다.", styles)
        }
    }

    private fun fileFormat(ready: Set<ExportFormat>): AtsFinding {
        val submittable = ready.filter { it == ExportFormat.DOCX || it == ExportFormat.PDF }
        return if (submittable.isNotEmpty()) {
            AtsFinding("FILE_FORMAT", AtsSeverity.PASS, "ATS가 읽는 형식의 파일이 있습니다.", submittable.map { it.name })
        } else {
            AtsFinding("FILE_FORMAT", AtsSeverity.INFO, "DOCX 또는 PDF 파일을 아직 만들지 않았습니다.")
        }
    }

    private fun length(
        type: DocumentType,
        text: String,
        pageCount: Int?,
    ): AtsFinding {
        val chars = text.replace(Regex("\\s+"), "").length
        return when {
            type == DocumentType.RESUME && pageCount != null && pageCount > RESUME_MAX_PAGES ->
                AtsFinding("LENGTH", AtsSeverity.INFO, "이력서가 ${pageCount}쪽입니다. 1~2쪽을 권장합니다.")
            chars > MAX_CHARS ->
                AtsFinding("LENGTH", AtsSeverity.INFO, "본문이 ${chars}자로 깁니다. 핵심 사실 위주로 줄이는 것을 권장합니다.")
            else -> AtsFinding("LENGTH", AtsSeverity.PASS, "분량이 권장 범위입니다.")
        }
    }

    private const val KEYWORD_THRESHOLD = 0.5
    private const val RESUME_MAX_PAGES = 2
    private const val MAX_CHARS = 4_000
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(\\+82[- ]?|0)1[0-9][- ]?\\d{3,4}[- ]?\\d{4}")
    private val DATE_STYLES =
        listOf(
            "2024.03" to Regex("\\b(19|20)\\d{2}\\.(0?[1-9]|1[0-2])\\b"),
            "2024-03" to Regex("\\b(19|20)\\d{2}-(0?[1-9]|1[0-2])\\b"),
            "2024년 3월" to Regex("(19|20)\\d{2}년\\s*(0?[1-9]|1[0-2])월"),
        )
}
