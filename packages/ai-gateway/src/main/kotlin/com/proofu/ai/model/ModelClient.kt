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

/**
 * Why a provider call failed, so callers can retry the transient kinds and page an operator for
 * the rest. Billing and configuration failures never fix themselves.
 */
enum class ProviderFailure(
    val retryable: Boolean,
    /** Someone has to act (top up credits, rotate a key, fix a request shape). */
    val operatorAction: Boolean,
) {
    BILLING(retryable = false, operatorAction = true),
    AUTHENTICATION(retryable = false, operatorAction = true),
    INVALID_REQUEST(retryable = false, operatorAction = true),
    RATE_LIMITED(retryable = true, operatorAction = false),
    OVERLOADED(retryable = true, operatorAction = false),
    UNAVAILABLE(retryable = true, operatorAction = false),
    ;

    companion object {
        /** From an HTTP status (null for transport errors) and the provider's message. */
        fun classify(
            statusCode: Int?,
            message: String?,
        ): ProviderFailure {
            val text = message?.lowercase().orEmpty()
            return when {
                statusCode == 400 && BILLING_HINTS.any { it in text } -> BILLING
                statusCode == 402 -> BILLING
                statusCode == 401 || statusCode == 403 -> AUTHENTICATION
                statusCode == 400 || statusCode == 404 || statusCode == 422 -> INVALID_REQUEST
                statusCode == 429 -> RATE_LIMITED
                statusCode == 529 -> OVERLOADED
                else -> UNAVAILABLE
            }
        }

        private val BILLING_HINTS = listOf("credit balance", "billing", "purchase credits", "payment")
    }
}

/** The provider call failed; [failure] says whether retrying makes sense. */
class ModelProviderException(
    message: String,
    cause: Throwable? = null,
    val failure: ProviderFailure = ProviderFailure.UNAVAILABLE,
) : RuntimeException(message, cause)
