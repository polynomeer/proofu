package com.proofu.ai

/**
 * Mutable binding target for `proofu.ai.*` (Spring Boot @ConfigurationProperties in the apps).
 * Kept here so api and worker read the same keys; converted with [toSettings].
 */
class AiGatewayProperties {
    var provider: String = "fake"
    var apiKey: String? = null
    var defaultModel: String = "claude-opus-5"
    var workspaceMonthlyBudgetUsd: Double = 5.0
    var workspaceHourlyJobLimit: Int = 30
    var deploymentDailyBudgetUsd: Double = 50.0
    var maxInputTokens: Int = 50_000

    fun toSettings() =
        AiGatewaySettings(
            provider = provider,
            apiKey = apiKey,
            defaultModel = defaultModel,
            workspaceMonthlyBudgetUsd = workspaceMonthlyBudgetUsd,
            workspaceHourlyJobLimit = workspaceHourlyJobLimit,
            deploymentDailyBudgetUsd = deploymentDailyBudgetUsd,
            maxInputTokens = maxInputTokens,
        )
}
