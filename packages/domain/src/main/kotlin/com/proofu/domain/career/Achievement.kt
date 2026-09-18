package com.proofu.domain.career

import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.math.BigDecimal

/** A measurable outcome. Describes a result, not an activity. */
data class Achievement(
    val id: AchievementId,
    val workspaceId: WorkspaceId,
    val projectId: ProjectId,
    val action: String,
    val outcome: String,
    val confidence: Confidence,
    val metricValue: BigDecimal? = null,
    val metricUnit: String? = null,
    val baseline: String? = null,
    val timeframe: String? = null,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(action.isNotBlank()) { "achievement action must not be blank" }
        domainRequire(outcome.isNotBlank()) { "achievement outcome must not be blank" }
        domainRequire(metricValue == null || !metricUnit.isNullOrBlank()) {
            "achievement with a metric value requires a metric unit"
        }
    }

    val hasMetric: Boolean get() = metricValue != null
}
