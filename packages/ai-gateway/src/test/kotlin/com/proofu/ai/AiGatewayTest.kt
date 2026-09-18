package com.proofu.ai

import com.proofu.ai.model.AiPurpose
import com.proofu.ai.model.ContextDocument
import com.proofu.ai.model.Effort
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class AiGatewayTest {
    private val recorded = mutableListOf<AiExecutionRecord>()
    private val fake = FakeModelClient()
    private var usage = AiUsageSnapshot(0, 0, 0)
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "fake", maxInputTokens = 1_000),
            usage = { _, _ -> usage },
            recorder = { recorded += it },
            clock = Clock.systemUTC(),
            ids = Uuid7IdGenerator(),
            client = fake,
        )
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val schema =
        """{"type":"object","required":["answer"],"properties":{"answer":{"type":"string"}},"additionalProperties":false}"""

    private fun call(vararg docs: ContextDocument) =
        AiCall(
            purpose = AiPurpose.REQUIREMENT_EXTRACTION,
            promptVersion = "extract-v1",
            systemPrompt = "You extract requirements.",
            documents = docs.toList(),
            instruction = "Extract.",
            outputSchema = schema,
        )

    private fun completed(text: String) =
        ModelOutcome.Completed(text, "claude-opus-5", ModelUsage(1_000, 200, 500, 0), truncated = false)

    @Test
    fun `valid output is validated, costed and recorded as succeeded`() {
        fake.enqueue(completed("""{"answer":"Kotlin"}"""))
        val doc = ContextDocument("snap-1", "Posting", "Kotlin 5+ years", Sensitivity.INTERNAL)

        val result = gateway.execute(workspace, call(doc))

        assertThat(result.json.get("answer").asString()).isEqualTo("Kotlin")
        assertThat(result.context.includedSourceIds).containsExactly("snap-1")
        // $5/M input, $25/M output, cache reads at 0.1× input
        assertThat(result.costMicros).isEqualTo(1_000 * 5 + 200 * 25 + 500L * 5 / 10)
        assertThat(recorded.single().status).isEqualTo(AiExecutionStatus.SUCCEEDED)
        assertThat(recorded.single().inputTokens).isEqualTo(1_500)
        assertThat(recorded.single().policyVersion).isEqualTo(AiGateway.POLICY_VERSION)
        assertThat(fake.requests.single().effort).isEqualTo(Effort.LOW)
        assertThat(
            fake.requests
                .single()
                .documents
                .single()
                .sourceId,
        ).isEqualTo("snap-1")
    }

    @Test
    fun `confidential documents never reach the provider without consent`() {
        fake.enqueue(completed("""{"answer":"x"}"""))
        val secret = ContextDocument("ev-9", "Internal report", "confidential numbers", Sensitivity.CONFIDENTIAL)

        val result = gateway.execute(workspace, call(secret))

        assertThat(fake.requests.single().documents).isEmpty()
        assertThat(result.context.excluded).containsKey("ev-9")
    }

    @Test
    fun `schema violations are recorded as rejected and thrown`() {
        fake.enqueue(completed("""{"answer":42}"""))

        assertThatThrownBy { gateway.execute(workspace, call()) }.isInstanceOf(AiCallFailed.InvalidOutput::class.java)
        assertThat(recorded.single().status).isEqualTo(AiExecutionStatus.REJECTED_BY_VALIDATION)
        assertThat(recorded.single().costMicros).isPositive() // the tokens were still spent
    }

    @Test
    fun `refusals are recorded as failed and thrown`() {
        fake.enqueue(ModelOutcome.Refused("cyber", "declined", ModelUsage(10, 0, 0, 0)))

        assertThatThrownBy { gateway.execute(workspace, call()) }.isInstanceOf(AiCallFailed.Refused::class.java)
        assertThat(recorded.single().status).isEqualTo(AiExecutionStatus.FAILED)
    }

    @Test
    fun `budget is checked before the provider is called`() {
        usage =
            AiUsageSnapshot(
                workspaceMonthSpentMicros = 5_000_000,
                workspaceJobsLastHour = 0,
                deploymentDaySpentMicros = 0,
            )

        assertThatThrownBy { gateway.execute(workspace, call()) }.isInstanceOf(AiBudgetExceeded::class.java)
        assertThat(fake.requests).isEmpty()
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `input hash ignores nothing the model saw`() {
        fake.enqueue(completed("""{"answer":"a"}"""), completed("""{"answer":"b"}"""))
        val doc = ContextDocument("s", "t", "same text", Sensitivity.PUBLIC)
        gateway.execute(workspace, call(doc))
        gateway.execute(workspace, call(doc.copy(text = "changed text")))
        assertThat(recorded[0].inputHash).isNotEqualTo(recorded[1].inputHash).hasSize(64)
    }
}
