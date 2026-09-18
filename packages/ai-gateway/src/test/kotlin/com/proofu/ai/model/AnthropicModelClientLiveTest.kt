package com.proofu.ai.model

import com.proofu.domain.common.Sensitivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Opt-in smoke test against the real API (costs a fraction of a cent). Runs only when
 * ANTHROPIC_API_KEY is set, so CI and keyless machines skip it. It proves the request shape
 * (cached system prompt, document blocks, schema-constrained output) is accepted end to end.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicModelClientLiveTest {
    @Test
    fun `schema-constrained extraction round trip`() {
        val client = AnthropicModelClient(System.getenv("ANTHROPIC_API_KEY"))
        val outcome =
            client.complete(
                ModelRequest(
                    model = "claude-opus-5",
                    purpose = AiPurpose.REQUIREMENT_EXTRACTION,
                    systemPrompt = "You extract job requirements. Answer only with JSON matching the schema.",
                    documents =
                        listOf(
                            ContextDocument(
                                sourceId = "snapshot-1",
                                title = "Job posting",
                                text = "[자격 요건]\n- Kotlin 5년 이상\n- PostgreSQL 운영 경험\n[우대]\n- Spring Boot",
                                sensitivity = Sensitivity.INTERNAL,
                            ),
                        ),
                    instruction =
                        "List each requirement with its exact quote from the document and the document's sourceId.",
                    outputSchema =
                        """
                        {"type":"object","additionalProperties":false,"required":["items"],
                         "properties":{"items":{"type":"array","items":{"type":"object","additionalProperties":false,
                           "required":["text","quote","sourceId"],
                           "properties":{"text":{"type":"string"},"quote":{"type":"string"},"sourceId":{"type":"string"}}}}}}
                        """.trimIndent(),
                    effort = Effort.LOW,
                    maxOutputTokens = 2_000,
                ),
            )

        assertThat(outcome).isInstanceOf(ModelOutcome.Completed::class.java)
        val completed = outcome as ModelOutcome.Completed
        assertThat(completed.text).contains("\"items\"").contains("snapshot-1")
        val seen =
            completed.usage.inputTokens + completed.usage.cacheReadInputTokens + completed.usage.cacheWriteInputTokens
        assertThat(seen).isPositive()
        println("model=${completed.model} usage=${completed.usage}\n${completed.text}")
    }
}
