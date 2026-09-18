package com.proofu.api.ai

import com.proofu.ai.AiExecutionRecord
import com.proofu.ai.AiExecutionStatus
import com.proofu.ai.AiGateway
import com.proofu.ai.jdbc.JdbcAiExecutionRecorder
import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class AiUsageApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Autowired
    lateinit var gateway: AiGateway

    @Test
    fun `usage reflects the ledger for the caller's workspace only`() {
        val support = ApiTestSupport(client, jdbc, mapper)
        val workspace = support.newWorkspace()
        val other = support.newWorkspace()
        val recorder = JdbcAiExecutionRecorder(jdbc)
        recorder.record(execution(workspace, 1_250_000))
        recorder.record(execution(workspace, 250_000))
        recorder.record(execution(other, 4_000_000))

        val usage = support.getJson(workspace, "/api/v1/ai/usage")
        assertThat(usage["provider"]).isEqualTo("fake")
        assertThat(usage["model"]).isEqualTo("claude-opus-5")
        assertThat((usage["monthSpentUsd"] as Number).toDouble()).isEqualTo(1.5)
        assertThat((usage["monthBudgetUsd"] as Number).toDouble()).isEqualTo(5.0)
        assertThat(usage["jobsLastHour"]).isEqualTo(2)
        assertThat(usage["hourlyLimit"]).isEqualTo(30)
        assertThat(usage["monthUsedPercent"]).isEqualTo(30)
        assertThat(gateway).isNotNull
    }

    private fun execution(
        workspace: UUID,
        costMicros: Long,
    ) = AiExecutionRecord(
        id = UUID.randomUUID(),
        workspaceId = workspace,
        jobId = null,
        purpose = "REQUIREMENT_EXTRACTION",
        provider = "fake",
        model = "claude-opus-5",
        promptVersion = "test",
        policyVersion = AiGateway.POLICY_VERSION,
        inputHash = "a".repeat(64),
        status = AiExecutionStatus.SUCCEEDED,
        inputTokens = 100,
        outputTokens = 10,
        costMicros = costMicros,
        latencyMs = 5,
    )
}
