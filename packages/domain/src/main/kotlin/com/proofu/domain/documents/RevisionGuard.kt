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
        val added = newNumbers(text, allowed)
        if (added.isNotEmpty()) problems += "revision adds numbers not in the sources: $added"
        return problems
    }

    /**
     * Every number a text states, in two spellings so date and thousands reformatting is not
     * mistaken for a new figure: the joined form (`2024.03` → `202403`, `1,200` → `1200`) and each
     * digit run without leading zeros (`2024.03` → `2024`, `3`; `2024년 3월` → `2024`, `3`).
     */
    fun numbers(text: String): List<String> =
        NUMBER
            .findAll(text)
            .flatMap { m ->
                val joined = m.value.replace(Regex("[,.]"), "")
                val runs = m.value.split(Regex("[,.]")).map { it.trimStart('0').ifEmpty { "0" } }
                (listOf(joined) + runs).distinct()
            }.toList()

    /** Figures in [text] that appear in none of their spellings among [allowed]. */
    fun newNumbers(
        text: String,
        allowed: Set<String>,
    ): List<String> =
        NUMBER
            .findAll(text)
            .map { it.value }
            .filter { value -> numbers(value).none { it in allowed } }
            .distinct()
            .toList()

    private const val MAX_GROWTH = 3
    private const val SLACK = 40
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)*")
}
