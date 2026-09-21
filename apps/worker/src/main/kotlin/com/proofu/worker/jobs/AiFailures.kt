package com.proofu.worker.jobs

import com.proofu.ai.AiBudgetExceeded
import com.proofu.ai.AiCallFailed
import com.proofu.ai.model.ModelProviderException
import com.proofu.ai.model.ProviderFailure
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

/**
 * One mapping from AI failures to job error codes (the API's ErrorCode names), shared by every
 * AI job. Provider failures that need a person — billing, credentials, a broken request shape —
 * are logged at ERROR with the OPERATOR_ACTION marker so alerting can key on it; the message
 * never includes prompt text or keys.
 */
object AiFailures {
    private val log = LoggerFactory.getLogger(AiFailures::class.java)
    val OPERATOR_ACTION = MarkerFactory.getMarker("OPERATOR_ACTION")

    /** Runs [block] and turns any AI failure into a [JobFailure]; other exceptions pass through. */
    inline fun <T> guard(block: () -> T): T =
        try {
            block()
        } catch (e: AiBudgetExceeded) {
            throw JobFailure("AI_BUDGET_EXCEEDED", e.message ?: "budget", retryable = false, cause = e)
        } catch (e: AiCallFailed.Refused) {
            throw JobFailure("AI_REFUSED", e.message ?: "refused", retryable = false, cause = e)
        } catch (e: AiCallFailed.InvalidOutput) {
            throw JobFailure("AI_OUTPUT_INVALID", e.message ?: "invalid output", retryable = true, cause = e)
        } catch (e: ModelProviderException) {
            throw fromProvider(e)
        }

    fun fromProvider(e: ModelProviderException): JobFailure {
        val code = codeFor(e.failure)
        if (e.failure.operatorAction) {
            log.error(OPERATOR_ACTION, "AI provider failure needs an operator: kind={} code={}", e.failure, code)
        }
        return JobFailure(code, e.message ?: "provider", retryable = e.failure.retryable, cause = e)
    }

    fun codeFor(failure: ProviderFailure): String =
        when (failure) {
            ProviderFailure.BILLING -> "AI_BILLING_BLOCKED"
            ProviderFailure.AUTHENTICATION, ProviderFailure.INVALID_REQUEST -> "AI_CONFIGURATION_ERROR"
            ProviderFailure.RATE_LIMITED -> "AI_RATE_LIMITED"
            ProviderFailure.OVERLOADED, ProviderFailure.UNAVAILABLE -> "AI_PROVIDER_UNAVAILABLE"
        }
}
