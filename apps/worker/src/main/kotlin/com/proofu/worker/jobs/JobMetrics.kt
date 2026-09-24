package com.proofu.worker.jobs

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * The queue signals the SLIs in docs/operations/monitoring.md. Workspace ids are never labels
 * (high cardinality); per-workspace numbers come from `ai_executions` and `audit_events`.
 */
@Component
class JobMetrics(
    private val registry: MeterRegistry,
    private val jdbc: JdbcTemplate,
) {
    private val queued = AtomicLong()
    private val running = AtomicLong()
    private val oldestAgeSeconds = AtomicLong()

    init {
        registry.gauge("proofu.jobs.queued", queued) { it.get().toDouble() }
        registry.gauge("proofu.jobs.running", running) { it.get().toDouble() }
        registry.gauge("proofu.jobs.oldest.age.seconds", oldestAgeSeconds) { it.get().toDouble() }
    }

    /** Counts one finished job. [outcome] is succeeded, retrying or failed — never the error text. */
    fun completed(
        type: String,
        outcome: String,
        durationNanos: Long,
    ) {
        Timer
            .builder("proofu.jobs.duration")
            .tag("type", type)
            .register(registry)
            .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS)
        registry.counter("proofu.jobs.completed", "type", type, "outcome", outcome).increment()
    }

    /** A job whose worker vanished: back in the queue, or failed when its attempts ran out. */
    fun recovered(
        type: String,
        outcome: String,
    ) {
        registry.counter("proofu.jobs.recovered", "type", type, "outcome", outcome).increment()
    }

    /** Queue depth and the age of the oldest waiting job, refreshed on a timer, not per scrape. */
    @Scheduled(fixedDelayString = "\${proofu.worker.metrics-interval:15s}")
    fun refresh() {
        val row =
            jdbc.queryForMap(
                """
                select
                  count(*) filter (where status = 'QUEUED') as queued,
                  count(*) filter (where status = 'RUNNING') as running,
                  coalesce(extract(epoch from now() - min(scheduled_at) filter (where status = 'QUEUED')), 0) as oldest
                from jobs
                """.trimIndent(),
            )
        queued.set((row["queued"] as Number).toLong())
        running.set((row["running"] as Number).toLong())
        oldestAgeSeconds.set((row["oldest"] as Number).toLong())
    }
}
