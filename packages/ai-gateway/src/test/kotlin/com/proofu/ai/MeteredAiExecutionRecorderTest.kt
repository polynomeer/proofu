package com.proofu.ai

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class MeteredAiExecutionRecorderTest {
    private val registry = SimpleMeterRegistry()

    private fun record(
        status: AiExecutionStatus = AiExecutionStatus.SUCCEEDED,
        purpose: String = "POSTING_ANALYSIS",
        costMicros: Long? = 1_500_000,
        latencyMs: Int = 1_200,
    ) = AiExecutionRecord(
        id = UUID.randomUUID(),
        workspaceId = UUID.randomUUID(),
        jobId = UUID.randomUUID(),
        purpose = purpose,
        provider = "anthropic",
        model = "claude-opus-5",
        promptVersion = "1",
        policyVersion = "1",
        inputHash = "hash",
        status = status,
        inputTokens = 100,
        outputTokens = 50,
        costMicros = costMicros,
        latencyMs = latencyMs,
    )

    private fun counter(
        name: String,
        vararg tags: String,
    ) = registry
        .find(name)
        .tags(*tags)
        .counter()
        ?.count()

    @Test
    fun `an execution is counted once, with its cost in dollars and its latency`() {
        val recorded = mutableListOf<AiExecutionRecord>()
        val metered = MeteredAiExecutionRecorder(recorded::add, registry)

        metered.record(record())

        assertThat(recorded).hasSize(1)
        assertThat(counter("proofu.ai.executions", "purpose", "POSTING_ANALYSIS", "status", "SUCCEEDED")).isEqualTo(1.0)
        assertThat(counter("proofu.ai.cost.usd", "purpose", "POSTING_ANALYSIS")).isEqualTo(1.5)
        assertThat(registry.find("proofu.ai.latency").timer()?.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isEqualTo(1_200.0)
    }

    @Test
    fun `a failed call still counts, and a call with no cost does not pretend to be free of charge`() {
        val metered = MeteredAiExecutionRecorder({}, registry)

        metered.record(record(status = AiExecutionStatus.FAILED, costMicros = null))

        assertThat(counter("proofu.ai.executions", "purpose", "POSTING_ANALYSIS", "status", "FAILED")).isEqualTo(1.0)
        // No cost series at all is honest; a 0 would be read as "this call was free".
        assertThat(registry.find("proofu.ai.cost.usd").counter()).isNull()
    }

    @Test
    fun `the audit row comes first - if it fails, nothing is counted`() {
        val metered =
            MeteredAiExecutionRecorder({ throw IllegalStateException("ai_executions insert failed") }, registry)

        assertFailsWith<IllegalStateException> { metered.record(record()) }

        assertThat(registry.find("proofu.ai.executions").counter()).isNull()
    }
}
