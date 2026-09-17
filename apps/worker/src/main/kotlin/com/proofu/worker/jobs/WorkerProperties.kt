package com.proofu.worker.jobs

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "proofu.worker")
data class WorkerProperties(
    /** Delay between polls of the jobs table when the previous poll found nothing. */
    val pollInterval: Duration = Duration.ofSeconds(2),
    /** Maximum jobs claimed per poll. */
    val batchSize: Int = 5,
)
