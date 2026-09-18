package com.proofu.ai.jdbc

import com.proofu.ai.AiExecutionRecord
import com.proofu.ai.AiExecutionRecorder
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.AiUsageSource
import com.proofu.domain.common.WorkspaceId
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** ai_executions as the ledger for both the audit trail and the budget guard. */
class JdbcAiExecutionRecorder(
    private val jdbc: JdbcTemplate,
) : AiExecutionRecorder {
    override fun record(record: AiExecutionRecord) {
        jdbc.update(
            """
            insert into ai_executions
              (id, workspace_id, job_id, purpose, provider, model, prompt_version, policy_version, input_hash,
               status, input_tokens, output_tokens, cost_micros, latency_ms)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            record.id,
            record.workspaceId,
            record.jobId,
            record.purpose,
            record.provider,
            record.model,
            record.promptVersion,
            record.policyVersion,
            record.inputHash,
            record.status.name,
            record.inputTokens,
            record.outputTokens,
            record.costMicros,
            record.latencyMs,
        )
    }
}

/**
 * Budget windows: the workspace month is the calendar month in UTC, the hourly window is
 * rolling, and the deployment day is the UTC calendar day. Failed calls count toward the
 * hourly limit (they cost a request) but only completed calls carry cost.
 */
class JdbcAiUsageSource(
    private val jdbc: JdbcTemplate,
) : AiUsageSource {
    override fun usage(
        workspace: WorkspaceId,
        now: Instant,
    ): AiUsageSnapshot {
        val monthStart =
            now
                .atZone(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
        val dayStart = now.truncatedTo(ChronoUnit.DAYS)
        val hourAgo = now.minus(1, ChronoUnit.HOURS)
        return jdbc.queryForObject(
            """
            select
              coalesce((select sum(cost_micros) from ai_executions where workspace_id = ? and created_at >= ?), 0) as month_spent,
              (select count(*) from ai_executions where workspace_id = ? and created_at >= ?) as hour_jobs,
              coalesce((select sum(cost_micros) from ai_executions where created_at >= ?), 0) as day_spent
            """.trimIndent(),
            { rs, _ -> AiUsageSnapshot(rs.getLong("month_spent"), rs.getInt("hour_jobs"), rs.getLong("day_spent")) },
            workspace.value,
            Timestamp.from(monthStart),
            workspace.value,
            Timestamp.from(hourAgo),
            Timestamp.from(dayStart),
        )
    }
}
