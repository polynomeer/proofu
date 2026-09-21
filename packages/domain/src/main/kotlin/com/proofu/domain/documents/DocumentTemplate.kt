package com.proofu.domain.documents

/** A section of a document template. Block ids are `<section id>-<n>` so they stay stable across versions. */
data class TemplateSection(
    val id: String,
    val title: String,
    val guidance: String,
) {
    fun blockId(index: Int): String = "$id-$index"
}

/**
 * Content templates (documents §"콘텐츠 모델"). Templates only fix the section order and what
 * each section is for; they never change data. The version is recorded on every document version.
 */
data class DocumentTemplate(
    val version: String,
    val type: DocumentType,
    val sections: List<TemplateSection>,
    /** BCP 47 primary tag of the section titles and guidance (`ko`, `en`). */
    val language: String = "ko",
) {
    init {
        require(sections.isNotEmpty()) { "template needs at least one section" }
        val ids = sections.map { it.id }
        require(ids.toSet().size == ids.size) { "duplicate section ids in template $version" }
        require(ids.all { SECTION_ID.matches(it) }) { "section ids must be lowercase words: $ids" }
    }

    fun section(id: String): TemplateSection? = sections.firstOrNull { it.id == id }

    /** Section id of a block id produced by [TemplateSection.blockId]. */
    fun sectionOf(blockId: String): TemplateSection? = section(blockId.substringBeforeLast('-'))

    companion object {
        private val SECTION_ID = Regex("[a-z]+")
        const val KO_V1 = "ko-v1"

        private val RESUME_KO =
            DocumentTemplate(
                KO_V1,
                DocumentType.RESUME,
                listOf(
                    TemplateSection("summary", "요약", "지원 직무와 연결되는 핵심 경력을 2~3문장으로 요약"),
                    TemplateSection("skills", "핵심 역량", "요구사항과 직접 연결되는 역량을 근거와 함께 한 줄씩"),
                    TemplateSection("career", "경력", "경력 기록 단위로 역할과 사실"),
                    TemplateSection("projects", "프로젝트", "프로젝트별 문제·행동·결과"),
                    TemplateSection("techs", "기술", "실제 사용한 기술만"),
                    TemplateSection("education", "학력", "기록에 있는 학력만"),
                ),
            )
        private val COVER_LETTER_KO =
            DocumentTemplate(
                KO_V1,
                DocumentType.COVER_LETTER,
                listOf(
                    TemplateSection("motivation", "지원 동기", "공고 요구사항과 본인 경력의 접점"),
                    TemplateSection("experience", "관련 경험", "요구사항별로 뒷받침하는 경험을 근거와 함께"),
                    TemplateSection("contribution", "기여 계획", "기록된 사실에서 도출 가능한 범위의 계획"),
                ),
            )
        private val PORTFOLIO_KO =
            DocumentTemplate(
                KO_V1,
                DocumentType.PORTFOLIO,
                listOf(
                    TemplateSection("intro", "소개", "역할과 관심 영역"),
                    TemplateSection("cases", "사례", "사례별 문제·행동·결과"),
                    TemplateSection("links", "증빙 링크", "공개 가능한 Evidence 링크만"),
                ),
            )

        const val EN_V1 = "en-v1"

        private val RESUME_EN =
            DocumentTemplate(
                EN_V1,
                DocumentType.RESUME,
                listOf(
                    TemplateSection(
                        "summary",
                        "Summary",
                        "Two or three sentences tying the strongest facts to the role",
                    ),
                    TemplateSection(
                        "skills",
                        "Core Skills",
                        "One line per skill that maps to a requirement, with the fact behind it",
                    ),
                    TemplateSection("career", "Experience", "Role and facts per career entry"),
                    TemplateSection("projects", "Projects", "Problem, action and result per project"),
                    TemplateSection("techs", "Technologies", "Only technologies actually used"),
                    TemplateSection("education", "Education", "Only recorded education"),
                ),
                language = "en",
            )
        private val COVER_LETTER_EN =
            DocumentTemplate(
                EN_V1,
                DocumentType.COVER_LETTER,
                listOf(
                    TemplateSection(
                        "motivation",
                        "Why this role",
                        "Where the posting's requirements meet the candidate's record",
                    ),
                    TemplateSection(
                        "experience",
                        "Relevant experience",
                        "Experience that supports each requirement, with the facts",
                    ),
                    TemplateSection(
                        "contribution",
                        "How I would contribute",
                        "Plans that follow from recorded facts only",
                    ),
                ),
                language = "en",
            )
        private val PORTFOLIO_EN =
            DocumentTemplate(
                EN_V1,
                DocumentType.PORTFOLIO,
                listOf(
                    TemplateSection("intro", "About", "Role and areas of interest"),
                    TemplateSection("cases", "Case studies", "Problem, action and result per case"),
                    TemplateSection("links", "Links", "Publicly shareable evidence only"),
                ),
                language = "en",
            )

        private val ALL = listOf(RESUME_KO, COVER_LETTER_KO, PORTFOLIO_KO, RESUME_EN, COVER_LETTER_EN, PORTFOLIO_EN)

        fun find(
            version: String,
            type: DocumentType,
        ): DocumentTemplate? = ALL.firstOrNull { it.version == version && it.type == type }

        /** The current template for a document language; unknown languages get the Korean one. */
        fun latest(
            type: DocumentType,
            language: String = "ko",
        ): DocumentTemplate = find(if (language.lowercase().startsWith("en")) EN_V1 else KO_V1, type)!!
    }
}
