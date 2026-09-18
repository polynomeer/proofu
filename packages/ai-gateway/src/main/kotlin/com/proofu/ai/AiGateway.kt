package com.proofu.ai

import com.proofu.ai.cost.ModelPricing
import com.proofu.ai.model.AiPurpose
import com.proofu.ai.model.ContextDocument
import com.proofu.ai.model.Effort
import com.proofu.ai.model.ModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelProviderException
import com.proofu.ai.model.ModelRequest
import com.proofu.ai.policy.ContextDecision
import com.proofu.ai.policy.ContextPolicy
import com.proofu.ai.schema.OutputSchemaValidator
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.WorkspaceId
import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Everything a feature needs to say about a model call; the gateway adds model, policy and bookkeeping. */
data class AiCall(
    val purpose: AiPurpose,
    val promptVersion: String,
    val systemPrompt: String,
    val documents: List<ContextDocument>,
    val instruction: String,
    val outputSchema: String,
    val effort: Effort = defaultEffort(purpose),
    val maxOutputTokens: Int = defaultMaxOutput(purpose),
    /** The user explicitly allowed CONFIDENTIAL/RESTRICTED documents for this call (UX §7.4). */
    val consentToSensitive: Boolean = false,
    val jobId: UUID? = null,
) {
    companion object {
        /** ADR-0008 §3 per-task defaults. */
        fun defaultEffort(purpose: AiPurpose): Effort =
            when (purpose) {
                AiPurpose.REQUIREMENT_EXTRACTION, AiPurpose.REVIEW_HYPOTHESIS -> Effort.LOW
                AiPurpose.MATCH_EXPLANATION, AiPurpose.SENTENCE_REVISION -> Effort.MEDIUM
                AiPurpose.DOCUMENT_GENERATION -> Effort.HIGH
            }

        fun defaultMaxOutput(purpose: AiPurpose): Int =
            when (purpose) {
                AiPurpose.DOCUMENT_GENERATION -> 16_000
                else -> 8_000
            }
    }
}

data class AiResult(
    val executionId: UUID,
    val model: String,
    val json: JsonNode,
    val context: ContextDecision,
    val costMicros: Long,
    val truncated: Boolean,
)

sealed class AiCallFailed(
    message: String,
) : RuntimeException(message) {
    class Refused(
        val category: String?,
        val explanation: String?,
    ) : AiCallFailed("model refused the request${category?.let { " ($it)" } ?: ""}")

    class InvalidOutput(
        val problems: List<String>,
    ) : AiCallFailed("model output failed schema validation: ${problems.joinToString("; ")}")
}

/**
 * The one door to model providers (ADR-0005/0008): context policy -> budget -> provider ->
 * schema validation -> cost -> audit row. Feature code never sees the provider and never
 * reads unvalidated output. Reference whitelisting against [ContextDecision.includedSourceIds]
 * is the caller's job because only it knows which fields hold ids.
 */
class AiGateway(
    private val client: ModelClient,
    private val defaultModel: String,
    private val provider: String,
    private val policy: ContextPolicy,
    private val budget: AiBudgetGuard,
    private val recorder: AiExecutionRecorder,
    private val validator: OutputSchemaValidator,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    fun execute(
        workspace: WorkspaceId,
        call: AiCall,
    ): AiResult {
        val now = Instant.now(clock)
        budget.check(workspace, now)

        val reserved = ContextPolicy.estimateTokens(call.systemPrompt) + ContextPolicy.estimateTokens(call.instruction)
        val context = policy.apply(call.documents, call.consentToSensitive, reserved)
        val request =
            ModelRequest(
                model = defaultModel,
                purpose = call.purpose,
                systemPrompt = call.systemPrompt,
                documents = context.included,
                instruction = call.instruction,
                outputSchema = call.outputSchema,
                effort = call.effort,
                maxOutputTokens = call.maxOutputTokens,
            )
        val executionId = ids.next()
        val inputHash = hash(request)
        val started = System.nanoTime()

        val outcome =
            try {
                client.complete(request)
            } catch (e: ModelProviderException) {
                record(executionId, workspace, call, request, inputHash, AiExecutionStatus.FAILED, null, null, started)
                throw e
            }
        return when (outcome) {
            is ModelOutcome.Refused -> {
                record(
                    executionId,
                    workspace,
                    call,
                    request,
                    inputHash,
                    AiExecutionStatus.FAILED,
                    outcome.usage,
                    null,
                    started,
                )
                throw AiCallFailed.Refused(outcome.category, outcome.explanation)
            }
            is ModelOutcome.Completed -> {
                val cost = ModelPricing.costMicros(outcome.model, outcome.usage)
                when (val validated = validator.validate(call.outputSchema, outcome.text)) {
                    is OutputSchemaValidator.Result.Invalid -> {
                        record(
                            executionId,
                            workspace,
                            call,
                            request,
                            inputHash,
                            AiExecutionStatus.REJECTED_BY_VALIDATION,
                            outcome.usage,
                            cost,
                            started,
                        )
                        throw AiCallFailed.InvalidOutput(validated.problems)
                    }
                    is OutputSchemaValidator.Result.Valid -> {
                        record(
                            executionId,
                            workspace,
                            call,
                            request,
                            inputHash,
                            AiExecutionStatus.SUCCEEDED,
                            outcome.usage,
                            cost,
                            started,
                        )
                        AiResult(executionId, outcome.model, validated.json, context, cost, outcome.truncated)
                    }
                }
            }
        }
    }

    private fun record(
        id: UUID,
        workspace: WorkspaceId,
        call: AiCall,
        request: ModelRequest,
        inputHash: String,
        status: AiExecutionStatus,
        usage: com.proofu.ai.model.ModelUsage?,
        cost: Long?,
        startedNanos: Long,
    ) {
        recorder.record(
            AiExecutionRecord(
                id = id,
                workspaceId = workspace.value,
                jobId = call.jobId,
                purpose = call.purpose.name,
                provider = provider,
                model = request.model,
                promptVersion = call.promptVersion,
                policyVersion = POLICY_VERSION,
                inputHash = inputHash,
                status = status,
                inputTokens = usage?.let { it.inputTokens + it.cacheReadInputTokens + it.cacheWriteInputTokens },
                outputTokens = usage?.outputTokens,
                costMicros = cost,
                latencyMs = ((System.nanoTime() - startedNanos) / 1_000_000).toInt(),
            ),
        )
    }

    /** Stable hash of everything the model saw; lets identical calls be recognised without storing the text. */
    private fun hash(request: ModelRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")

        fun feed(s: String) = digest.update(s.toByteArray(Charsets.UTF_8)).also { digest.update(0) }
        feed(request.model)
        feed(request.purpose.name)
        feed(request.systemPrompt)
        request.documents.forEach {
            feed(it.sourceId)
            feed(it.text)
        }
        feed(request.instruction)
        feed(request.outputSchema)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Bump when ContextPolicy or validation semantics change; recorded on every execution. */
        const val POLICY_VERSION = "2026-09-18.1"
    }
}
