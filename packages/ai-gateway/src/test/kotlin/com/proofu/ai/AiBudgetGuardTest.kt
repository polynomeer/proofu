package com.proofu.ai

import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AiBudgetGuardTest {
    private val limits =
        AiGatewaySettings(
            workspaceMonthlyBudgetUsd = 5.0,
            workspaceHourlyJobLimit = 30,
            deploymentDailyBudgetUsd = 50.0,
        ).limits()
    private val workspace = WorkspaceId(UUID.randomUUID())

    private fun guard(usage: AiUsageSnapshot) = AiBudgetGuard(limits) { _, _ -> usage }

    @Test
    fun `under every limit passes and returns the snapshot`() {
        val usage = AiUsageSnapshot(4_990_000, 29, 49_000_000)
        assertThat(guard(usage).check(workspace, Instant.EPOCH)).isEqualTo(usage)
    }

    @Test
    fun `each limit blocks with its own reason, deployment first`() {
        assertThatThrownBy { guard(AiUsageSnapshot(0, 0, 50_000_000)).check(workspace, Instant.EPOCH) }
            .isInstanceOf(AiBudgetExceeded::class.java)
            .extracting("block")
            .isEqualTo(BudgetBlock.DEPLOYMENT_DAILY_BUDGET)
        assertThatThrownBy { guard(AiUsageSnapshot(5_000_000, 0, 0)).check(workspace, Instant.EPOCH) }
            .extracting("block")
            .isEqualTo(BudgetBlock.WORKSPACE_MONTHLY_BUDGET)
        assertThatThrownBy { guard(AiUsageSnapshot(0, 30, 0)).check(workspace, Instant.EPOCH) }
            .extracting("block")
            .isEqualTo(BudgetBlock.WORKSPACE_HOURLY_LIMIT)
    }
}
