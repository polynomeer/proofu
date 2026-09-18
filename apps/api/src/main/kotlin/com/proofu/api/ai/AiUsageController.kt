package com.proofu.api.ai

import com.proofu.ai.AiGatewayProperties
import com.proofu.ai.AiUsageSource
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant

data class AiUsageResponse(
    val provider: String,
    val model: String,
    val monthSpentUsd: Double,
    val monthBudgetUsd: Double,
    val jobsLastHour: Int,
    val hourlyLimit: Int,
) {
    /** 0–100, capped; the UI shows this next to the number, never as a probability of anything. */
    val monthUsedPercent: Int get() =
        if (monthBudgetUsd <=
            0
        ) {
            100
        } else {
            minOf(100, (monthSpentUsd * 100 / monthBudgetUsd).toInt())
        }
}

/** Lets the workspace see its AI budget (ADR-0008 §6); deployment-wide limits are not exposed. */
@RestController
@RequestMapping("${ApiPaths.V1}/ai")
class AiUsageController(
    private val usage: AiUsageSource,
    private val properties: AiGatewayProperties,
    private val clock: Clock,
) {
    @GetMapping("/usage")
    fun usage(workspace: WorkspaceContext): AiUsageResponse {
        val snapshot = usage.usage(workspace.workspaceId, Instant.now(clock))
        return AiUsageResponse(
            provider = properties.provider,
            model = properties.defaultModel,
            monthSpentUsd = snapshot.workspaceMonthSpentMicros / 1_000_000.0,
            monthBudgetUsd = properties.workspaceMonthlyBudgetUsd,
            jobsLastHour = snapshot.workspaceJobsLastHour,
            hourlyLimit = properties.workspaceHourlyJobLimit,
        )
    }
}
