package com.proofu.domain.documents

/** How the user wants a sentence changed; the model may not change what it says. */
enum class RevisionMode {
    SHORTEN,
    CLARIFY,
    FORMAL,
}

/**
 * Deterministic guard for AI sentence revisions (AI feature spec "문장 개선": 사실 의미 변경 금지).
 * A revision is refused when it introduces a number that neither the original nor the cited
 * facts contain, is empty, or grows far beyond the original. Returns the problems; empty = ok.
 */
object RevisionGuard {
    fun check(
        original: String,
        revised: String,
        facts: List<String> = emptyList(),
    ): List<String> {
        val problems = mutableListOf<String>()
        val text = revised.trim()
        if (text.isEmpty()) problems += "empty revision"
        if (text.length >
            original.trim().length * MAX_GROWTH + SLACK
        ) {
            problems += "revision is much longer than the original"
        }
        val allowed = (listOf(original) + facts).flatMap(::numbers).toSet()
        val added = numbers(text).filter { it !in allowed }.distinct()
        if (added.isNotEmpty()) problems += "revision adds numbers not in the sources: $added"
        return problems
    }

    /** Digit runs with separators (40%, 1,200, 2024.03, 7일) normalised to bare digits. */
    fun numbers(text: String): List<String> = NUMBER.findAll(text).map { it.value.replace(Regex("[,.]"), "") }.toList()

    private const val MAX_GROWTH = 3
    private const val SLACK = 40
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)*")
}
