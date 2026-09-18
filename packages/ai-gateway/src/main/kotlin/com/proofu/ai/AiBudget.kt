package com.proofu.ai

import com.proofu.domain.common.WorkspaceId
import java.time.Instant
import java.util.UUID

/** Limits from ADR-0008 §6; all money in micro-dollars. */
data class AiLimits(
    val workspaceMonthlyBudgetMicros: Long,
    val workspaceHourlyJobLimit: Int,
    val deploymentDailyBudgetMicros: Long,
    val maxInputTokens: Int,
)

data class AiUsageSnapshot(
    val workspaceMonthSpentMicros: Long,
    val workspaceJobsLastHour: Int,
    val deploymentDaySpentMicros: Long,
)

/** Why a call was not made. Surfaces to the API as 429 RATE_LIMITED with a user-facing reason. */
enum class BudgetBlock {
    WORKSPACE_MONTHLY_BUDGET,
    WORKSPACE_HOURLY_LIMIT,
    DEPLOYMENT_DAILY_BUDGET,
}

class AiBudgetExceeded(
    val block: BudgetBlock,
    val usage: AiUsageSnapshot,
) : RuntimeException("ai budget exceeded: $block")

fun interface AiUsageSource {
    fun usage(
        workspace: WorkspaceId,
        now: Instant,
    ): AiUsageSnapshot
}

/** Checks the three ADR-0008 limits before a call is made. */
class AiBudgetGuard(
    private val limits: AiLimits,
    private val source: AiUsageSource,
) {
    fun check(
        workspace: WorkspaceId,
        now: Instant,
    ): AiUsageSnapshot {
        val usage = source.usage(workspace, now)
        val overDay = usage.deploymentDaySpentMicros >= limits.deploymentDailyBudgetMicros
        val overMonth = usage.workspaceMonthSpentMicros >= limits.workspaceMonthlyBudgetMicros
        val overHour = usage.workspaceJobsLastHour >= limits.workspaceHourlyJobLimit
        val block =
            when {
                overDay -> BudgetBlock.DEPLOYMENT_DAILY_BUDGET
                overMonth -> BudgetBlock.WORKSPACE_MONTHLY_BUDGET
                overHour -> BudgetBlock.WORKSPACE_HOURLY_LIMIT
                else -> null
            }
        if (block != null) throw AiBudgetExceeded(block, usage)
        return usage
    }
}

enum class AiExecutionStatus {
    SUCCEEDED,
    FAILED,
    REJECTED_BY_VALIDATION,
}

/** One row of ai_executions. Never carries prompt or output text (security §12.2). */
data class AiExecutionRecord(
    val id: UUID,
    val workspaceId: UUID,
    val jobId: UUID?,
    val purpose: String,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val policyVersion: String,
    val inputHash: String,
    val status: AiExecutionStatus,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costMicros: Long?,
    val latencyMs: Int,
)

fun interface AiExecutionRecorder {
    fun record(record: AiExecutionRecord)
}
