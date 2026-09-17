package com.proofu.worker

import com.proofu.worker.jobs.WorkerProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties::class)
class ProofuWorkerApplication

fun main(args: Array<String>) {
    runApplication<ProofuWorkerApplication>(*args)
}
