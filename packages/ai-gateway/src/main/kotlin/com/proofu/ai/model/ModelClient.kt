package com.proofu.ai.model

import com.proofu.domain.common.Sensitivity

/** What a model call is for; drives the effort default, the prompt version and the audit trail. */
enum class AiPurpose {
    REQUIREMENT_EXTRACTION,
    MATCH_EXPLANATION,
    DOCUMENT_GENERATION,
    SENTENCE_REVISION,
    REVIEW_HYPOTHESIS,
}

enum class Effort {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
}

/**
 * A piece of user or third-party text handed to the model as *data*, never as instructions
 * (grounding policy §9.2 step 5). [sourceId] lets the caller whitelist what the model may cite.
 */
data class ContextDocument(
    val sourceId: String,
    val title: String,
    val text: String,
    val sensitivity: Sensitivity,
)

data class ModelRequest(
    val model: String,
    val purpose: AiPurpose,
    /** Stable across calls of the same purpose so the provider can cache it. */
    val systemPrompt: String,
    val documents: List<ContextDocument>,
    /** The per-call instruction; volatile content goes here, after the cached prefix. */
    val instruction: String,
    /** JSON Schema (draft 2020-12) the response must satisfy. */
    val outputSchema: String,
    val effort: Effort,
    val maxOutputTokens: Int,
)

data class ModelUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadInputTokens: Long,
    val cacheWriteInputTokens: Long,
)

sealed interface ModelOutcome {
    val usage: ModelUsage

    /** The provider returned text; whether it satisfies the schema is decided by the gateway. */
    data class Completed(
        val text: String,
        val model: String,
        override val usage: ModelUsage,
        val truncated: Boolean,
    ) : ModelOutcome

    /** The provider declined on policy grounds; nothing usable came back. */
    data class Refused(
        val category: String?,
        val explanation: String?,
        override val usage: ModelUsage,
    ) : ModelOutcome
}

/** Provider boundary (ADR-0005). Implementations must not interpret the output. */
fun interface ModelClient {
    fun complete(request: ModelRequest): ModelOutcome
}

/** The provider is unreachable or failed; retryable at the job level. */
class ModelProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
