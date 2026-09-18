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

        private fun isHeader(line: String) =
            line.startsWith("[") || line.endsWith(":") || (!line.startsWith("-") && !line.startsWith("•"))

        private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private const val MAX_ITEMS = 20
    }
}
