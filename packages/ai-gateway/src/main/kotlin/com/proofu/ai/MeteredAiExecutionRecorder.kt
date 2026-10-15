package com.proofu.ai

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

/**
 * The Prometheus view of what the gateway spends, wrapped around the real recorder so every
 * execution — succeeded, failed or rejected by validation — is counted exactly once
 * (`docs/operations/monitoring.md` §SLI). The audit row is written first: a metric is never
 * worth losing the record.
 *
 * Workspace ids are not labels (high cardinality, ADR-0004 §관측성); per-workspace spend is a
 * query over `ai_executions`. `purpose` and `status` are enum names, so the series stay bounded.
 */
class MeteredAiExecutionRecorder(
    private val delegate: AiExecutionRecorder,
    private val registry: MeterRegistry,
) : AiExecutionRecorder {
    override fun record(record: AiExecutionRecord) {
        delegate.record(record)
        registry
            .counter("proofu.ai.executions", "purpose", record.purpose, "status", record.status.name)
            .increment()
        // Dollars, not micro-dollars: the budget in AiGatewaySettings and the alert threshold
        // are both in dollars, and a rule that has to divide by a million invites a typo.
        record.costMicros?.let {
            registry.counter("proofu.ai.cost.usd", "purpose", record.purpose).increment(it / MICROS_PER_USD)
        }
        Timer
            .builder("proofu.ai.latency")
            .tag("purpose", record.purpose)
            .register(registry)
            .record(record.latencyMs.toLong(), TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val MICROS_PER_USD = 1_000_000.0
    }
}
