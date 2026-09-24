package com.proofu.worker.jobs

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "proofu.worker")
data class WorkerProperties(
    /** Delay between polls of the jobs table when the previous poll found nothing. */
    val pollInterval: Duration = Duration.ofSeconds(2),
    /** Maximum jobs claimed per poll. */
    val batchSize: Int = 5,
    /**
     * How long a claimed job may stay RUNNING before it is presumed lost with its worker.
     * Handlers must finish well inside this; work that cannot is split into several jobs.
     */
    val jobLease: Duration = Duration.ofMinutes(15),
)
