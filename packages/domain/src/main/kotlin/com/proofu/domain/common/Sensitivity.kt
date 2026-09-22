package com.proofu.domain.common

/** What the user allowed AI processing to see beyond PUBLIC/INTERNAL (settings S01). */
enum class AiConsent {
    NONE,
    CONFIDENTIAL,
}

/**
 * Data classification (security §12.1). Drives document generation and
 * external transfer filters, including what may enter an AI context.
 */
enum class Sensitivity {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
    ;

    /** CONFIDENTIAL and RESTRICTED data never enter an AI context without explicit consent. */
    val allowedInAiContextByDefault: Boolean
        get() = allowedInAiContext(AiConsent.NONE)

    /**
     * Consent unlocks CONFIDENTIAL only. RESTRICTED (identity documents, health, credentials —
     * threat-model §데이터 분류) is never sent to a model, whatever the user consented to.
     */
    fun allowedInAiContext(consent: AiConsent): Boolean =
        when (this) {
            PUBLIC, INTERNAL -> true
            CONFIDENTIAL -> consent == AiConsent.CONFIDENTIAL
            RESTRICTED -> false
        }
}
