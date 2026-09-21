package com.proofu.ai

import com.proofu.ai.model.AnthropicModelClient
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelClient
import com.proofu.ai.policy.ContextPolicy
import com.proofu.ai.schema.OutputSchemaValidator
import com.proofu.domain.common.IdGenerator
import java.time.Clock

/** Values from environment (docs/architecture/non-functional.md §환경 변수, ADR-0008 §6). */
data class AiGatewaySettings(
    /** `anthropic` or `fake`. Fake answers with scripted/placeholder JSON and never leaves the process. */
    val provider: String = "anthropic",
    val apiKey: String? = null,
    val defaultModel: String = "claude-opus-5",
    val workspaceMonthlyBudgetUsd: Double = 5.0,
    val workspaceHourlyJobLimit: Int = 30,
    val deploymentDailyBudgetUsd: Double = 50.0,
    val maxInputTokens: Int = 50_000,
) {
    /**
     * Production may never run on the fake provider: placeholder JSON would flow into
     * requirements and documents as if a model had produced it.
     */
    fun requireRealProviderWhen(production: Boolean): AiGatewaySettings {
        check(!production || provider != "fake") {
            "AI_PROVIDER=fake is not allowed under the production profile; set AI_PROVIDER=anthropic and ANTHROPIC_API_KEY"
        }
        return this
    }

    fun limits() =
        AiLimits(
            workspaceMonthlyBudgetMicros = (workspaceMonthlyBudgetUsd * 1_000_000).toLong(),
            workspaceHourlyJobLimit = workspaceHourlyJobLimit,
            deploymentDailyBudgetMicros = (deploymentDailyBudgetUsd * 1_000_000).toLong(),
            maxInputTokens = maxInputTokens,
        )
}

/** Builds a gateway the same way in api and worker so both enforce identical policy. */
object AiGatewayFactory {
    fun create(
        settings: AiGatewaySettings,
        usage: AiUsageSource,
        recorder: AiExecutionRecorder,
        clock: Clock,
        ids: IdGenerator,
        client: ModelClient = clientFor(settings),
    ): AiGateway =
        AiGateway(
            client = client,
            defaultModel = settings.defaultModel,
            provider = settings.provider,
            policy = ContextPolicy(settings.maxInputTokens),
            budget = AiBudgetGuard(settings.limits(), usage),
            recorder = recorder,
            validator = OutputSchemaValidator(),
            clock = clock,
            ids = ids,
        )

    fun clientFor(settings: AiGatewaySettings): ModelClient =
        when (settings.provider) {
            "anthropic" -> {
                val key = settings.apiKey?.takeIf { it.isNotBlank() }
                requireNotNull(
                    key,
                ) { "AI_PROVIDER=anthropic requires ANTHROPIC_API_KEY (or set AI_PROVIDER=fake for local work)" }
                AnthropicModelClient(key)
            }
            "fake" -> FakeModelClient()
            else -> throw IllegalArgumentException("unknown AI provider '${settings.provider}'")
        }
}
