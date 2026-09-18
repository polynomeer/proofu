package com.proofu.ai.model

import java.util.ArrayDeque

/**
 * Scripted provider for tests and for local development without an API key. Each call pops
 * the next scripted outcome; when the script is empty it answers with [fallback].
 */
class FakeModelClient(
    private val fallback: (ModelRequest) -> ModelOutcome = { request ->
        ModelOutcome.Completed(
            text = """{"fake":true,"purpose":"${request.purpose}"}""",
            model = request.model,
            usage = ModelUsage(100, 20, 0, 0),
            truncated = false,
        )
    },
) : ModelClient {
    private val script = ArrayDeque<ModelOutcome>()
    val requests = mutableListOf<ModelRequest>()

    fun enqueue(vararg outcomes: ModelOutcome) = apply { script.addAll(outcomes) }

    override fun complete(request: ModelRequest): ModelOutcome {
        requests += request
        return script.pollFirst() ?: fallback(request)
    }
}
