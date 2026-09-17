package com.proofu.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProofuApiApplication

fun main(args: Array<String>) {
    runApplication<ProofuApiApplication>(*args)
}
