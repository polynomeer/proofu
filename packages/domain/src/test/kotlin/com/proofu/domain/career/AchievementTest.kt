package com.proofu.domain.career

import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AchievementTest {
    private fun achievement(
        value: BigDecimal?,
        unit: String?,
    ) = Achievement(
        id = AchievementId(Fixtures.uuid(20)),
        workspaceId = Fixtures.workspaceA,
        projectId = ProjectId(Fixtures.uuid(21)),
        action = "Introduced read replicas",
        outcome = "p95 latency dropped",
        confidence = Confidence(0.8),
        metricValue = value,
        metricUnit = unit,
    )

    @Test
    fun `a metric value requires a unit`() {
        assertThatThrownBy { achievement(BigDecimal("40"), null) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { achievement(BigDecimal("40"), " ") }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `metric with unit is accepted`() {
        assertThat(achievement(BigDecimal("40"), "%").hasMetric).isTrue()
        assertThat(achievement(null, null).hasMetric).isFalse()
    }
}
