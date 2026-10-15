package com.proofu.ai.extraction

/**
 * Removes contact details from a pasted resume before the model sees it.
 *
 * The profile rule is that contact details live in the export header and never enter AI context
 * or logs (docs/domain/career-data-model.md §프로필). A resume usually opens with exactly those
 * details, so an import would walk straight through that rule unless the text is cleaned first.
 *
 * It is deliberately blunt: a false positive costs a redacted line in a proposal's quote, a
 * false negative sends someone's phone number to a provider. Nothing here is a validator —
 * `01012345678` and `010-1234-5678` are both treated as a phone number because both look like
 * one.
 */
object ResumeRedactor {
    const val MASK = "[삭제됨]"

    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    /**
     * Phone-ish: an international number, or 9–13 digits held together by dots or hyphens with
     * no spaces around them. The spacing matters — `2020.03 - 2023.02` is the single most
     * common line in a resume and it is not a phone number.
     */
    private val PHONE = Regex("""\+\d[\d \-.]{7,16}\d|(?<![\w-])\d[\d\-.]{7,14}\d(?![\w-])""")

    /** Long unbroken digit runs: national id, card, account number. */
    private val DIGITS = Regex("""(?<![\w-])\d{9,}(?![\w-])""")

    /** Two years in one token means a period, whatever the separators look like. */
    private val YEARS = Regex("""(19|20)\d{2}""")

    data class Redaction(
        val text: String,
        val count: Int,
    )

    fun redact(text: String): Redaction {
        var count = 0
        var out = text
        for (pattern in listOf(EMAIL, PHONE, DIGITS)) {
            out =
                pattern.replace(out) { match ->
                    if (YEARS.findAll(match.value).count() >= 2) {
                        match.value
                    } else {
                        count++
                        MASK
                    }
                }
        }
        return Redaction(out, count)
    }
}
