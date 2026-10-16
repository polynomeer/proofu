package com.proofu.ai.model

import java.util.ArrayDeque

/**
 * Scripted provider for tests and for local development without an API key. Each call pops
 * the next scripted outcome; when the script is empty it answers with [fallback].
 *
 * The default fallback is a deliberately naive heuristic (bullet lines become requirements)
 * so the UI flow can be exercised end to end. It is not a stand-in for model quality and
 * must never be used to judge extraction behaviour.
 */
class FakeModelClient(
    private val fallback: (ModelRequest) -> ModelOutcome = ::heuristic,
) : ModelClient {
    private val script = ArrayDeque<ModelOutcome>()
    val requests = mutableListOf<ModelRequest>()

    fun enqueue(vararg outcomes: ModelOutcome) = apply { script.addAll(outcomes) }

    override fun complete(request: ModelRequest): ModelOutcome {
        requests += request
        return script.pollFirst() ?: fallback(request)
    }

    companion object {
        fun heuristic(request: ModelRequest): ModelOutcome {
            val text =
                when (request.purpose) {
                    AiPurpose.REQUIREMENT_EXTRACTION -> extractionHeuristic(request)
                    AiPurpose.MATCH_EXPLANATION -> explanationHeuristic(request)
                    AiPurpose.DOCUMENT_GENERATION -> draftHeuristic(request)
                    AiPurpose.SENTENCE_REVISION -> revisionHeuristic(request)
                    AiPurpose.CAREER_IMPORT -> careerImportHeuristic(request)
                    else -> """{"fake":true,"purpose":"${request.purpose}"}"""
                }
            return ModelOutcome.Completed(text, request.model, ModelUsage(100, 20, 0, 0), truncated = false)
        }

        private fun extractionHeuristic(request: ModelRequest): String {
            val doc = request.documents.firstOrNull() ?: return """{"items":[]}"""
            var category = "REQUIRED"
            val items = mutableListOf<String>()
            doc.text.lines().forEach { raw ->
                val line = raw.trim()
                val lower = line.lowercase()
                when {
                    line.isEmpty() -> Unit
                    isHeader(line) -> {
                        category =
                            when {
                                "우대" in line || "nice" in lower || "preferred" in lower -> "PREFERRED"
                                "업무" in line || "책임" in line || "you'll do" in lower || "responsib" in lower ->
                                    "RESPONSIBILITY"
                                "자격" in line || "요건" in line || "requirement" in lower -> "REQUIRED"
                                else -> "SKIP"
                            }
                    }
                    category != "SKIP" && items.size < MAX_ITEMS -> {
                        val quote = line.removePrefix("-").removePrefix("•").trim()
                        items +=
                            """{"category":"$category","text":${json(
                                quote,
                            )},"quote":${json(quote)},"confidence":0.5,"sourceId":${json(doc.sourceId)}}"""
                    }
                }
            }
            return """{"items":[${items.joinToString(",")}]}"""
        }

        /** Echoes every offered pair with a placeholder reason so the UI flow can be exercised. */
        private fun explanationHeuristic(request: ModelRequest): String {
            val items =
                PAIR
                    .findAll(request.instruction)
                    .map { m ->
                        """{"requirementId":${json(m.groupValues[1])},"claimId":${json(m.groupValues[2])},""" +
                            """"reason":"(가짜 제공자) 점수 ${m.groupValues[3]}점 후보입니다. 실제 설명은 모델 연결 후 생성됩니다.",""" +
                            """"matchedRequirementPhrase":"","matchedEvidencePhrase":""}"""
                    }.toList()
            return """{"explanations":[${items.joinToString(",")}]}"""
        }

        private val PAIR = Regex("requirementId=([0-9a-f-]{36}) claimId=([0-9a-f-]{36}) \\(score (\\d+)\\)")

        /**
         * One UNSUPPORTED placeholder in the first section and one SUPPORTED block per claim
         * document in the second, echoing the ids the documents carry, so the approval flow
         * can be exercised without a key.
         */
        private fun draftHeuristic(request: ModelRequest): String {
            val sections = SECTION.findAll(request.instruction).map { it.groupValues[1] }.toList()
            if (sections.isEmpty()) return """{"blocks":[]}"""
            val blocks = mutableListOf<String>()
            blocks +=
                """{"section":${json(sections.first())},"text":"(가짜 제공자) 실제 문장은 모델 연결 후 생성됩니다.",""" +
                """"claimRefs":[],"evidenceRefs":[],"requirementRefs":[],"certainty":"UNSUPPORTED"}"""
            val body = sections.getOrElse(1) { sections.first() }
            request.documents.filter { it.sourceId.startsWith("claim:") }.forEach { doc ->
                val claimId = doc.sourceId.removePrefix("claim:")
                val text =
                    doc.text
                        .lines()
                        .firstOrNull { it.startsWith("주장: ") }
                        ?.removePrefix("주장: ") ?: ""
                val evidence = EVIDENCE.findAll(doc.text).map { json(it.groupValues[1]) }.joinToString(",")
                val requirements = REQUIREMENT.findAll(doc.text).map { json(it.groupValues[1]) }.joinToString(",")
                blocks +=
                    """{"section":${json(body)},"text":${json(text)},"claimRefs":[${json(claimId)}],""" +
                    """"evidenceRefs":[$evidence],"requirementRefs":[$requirements],"certainty":"SUPPORTED"}"""
            }
            return """{"blocks":[${blocks.joinToString(",")}]}"""
        }

        /**
         * Reads the dated lines a resume is mostly made of ("2020.03 - 2023.02 회사 / 직무") so the
         * import flow can be exercised without a key. It understands one line shape and nothing
         * else — a real model reads prose, and this does not pretend to.
         */
        private fun careerImportHeuristic(request: ModelRequest): String {
            val doc = request.documents.firstOrNull() ?: return """{"items":[]}"""
            val lines = doc.text.lines()
            val items = mutableListOf<String>()
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                val match = PERIOD.find(line) ?: return@forEachIndexed
                if (items.size >= MAX_ITEMS) return@forEachIndexed
                val rest = line.removeRange(match.range).trim().trim('/', '|', '-', ' ')
                if (rest.isEmpty()) return@forEachIndexed
                val parts = rest.split("/", "|").map { it.trim() }.filter { it.isNotEmpty() }
                val organization = if (parts.size > 1) parts.first() else null
                val title = if (parts.size > 1) parts.drop(1).joinToString(" ") else parts.first()
                val type =
                    when {
                        EDUCATION_WORDS.containsMatchIn(rest) -> "EDUCATION"
                        CERTIFICATE_WORDS.containsMatchIn(rest) -> "CERTIFICATION"
                        else -> "EMPLOYMENT"
                    }
                val description =
                    lines
                        .getOrNull(index + 1)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && PERIOD.find(it) == null && !it.endsWith(":") }
                val end = match.groupValues[2].takeIf { it.isNotBlank() }?.replace(Regex("[./]"), "-")
                items +=
                    buildString {
                        append("""{"type":"$type","title":${json(title)},""")
                        if (organization != null) append(""""organization":${json(organization)},""")
                        if (description != null) append(""""description":${json(description)},""")
                        append(""""startDate":${json(match.groupValues[1].replace(Regex("[./]"), "-"))},""")
                        if (end != null) append(""""endDate":${json(end)},""")
                        append(""""quote":${json(line)},"confidence":0.6,"sourceId":${json(doc.sourceId)}}""")
                    }
            }
            return """{"items":[${items.joinToString(",")}]}"""
        }

        /** `2020.03 - 2023.02`, `2020-03 ~ 현재`, `2017.03-2020.02`. */
        private val PERIOD =
            Regex("""(\d{4}[.\-/]\d{1,2})\s*[-~–]\s*(\d{4}[.\-/]\d{1,2})?(?:현재|present)?""")
        private val EDUCATION_WORDS = Regex("""학사|석사|박사|대학|학과|University|Bachelor|Master""")
        private val CERTIFICATE_WORDS = Regex("""자격증|certificate|certification""", RegexOption.IGNORE_CASE)

        /** Collapses whitespace and marks the proposal so the compare-and-apply flow can be exercised. */
        private fun revisionHeuristic(request: ModelRequest): String {
            val original = request.documents.firstOrNull { it.sourceId.startsWith("block:") }?.text ?: ""
            val revised = original.replace(Regex("\\s+"), " ").trim() + " (가짜 제공자 제안)"
            return """{"revised":${json(revised)},"changes":["공백을 정리했습니다 (가짜 제공자)"]}"""
        }

        private val SECTION = Regex("^- section=([a-z]+) ", RegexOption.MULTILINE)
        private val EVIDENCE = Regex("evidenceId=([0-9a-f-]{36})")
        private val REQUIREMENT = Regex("requirementId=([0-9a-f-]{36})")

        private fun isHeader(line: String) =
            line.startsWith("[") || line.endsWith(":") || (!line.startsWith("-") && !line.startsWith("•"))

        private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private const val MAX_ITEMS = 20
    }
}
