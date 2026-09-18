package com.proofu.ai.matching

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class MatchExplainerTest {
    private val fake = FakeModelClient()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "fake"),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = { },
            clock = Clock.systemUTC(),
            ids = Uuid7IdGenerator(),
            client = fake,
        )
    private val explainer = MatchExplainer(gateway)
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val req = UUID.randomUUID()
    private val claimA = UUID.randomUUID()
    private val claimSecret = UUID.randomUUID()

    private fun requirements() =
        listOf(
            RequirementToExplain(
                req,
                "REQUIRED",
                "SaaS 제품 기획 경험",
                listOf(
                    CandidateToExplain(
                        claimA,
                        "SaaS 온보딩 재설계를 주도",
                        "활성화율 40% 개선",
                        listOf("Q2 대시보드"),
                        Sensitivity.INTERNAL,
                        82,
                    ),
                    CandidateToExplain(claimSecret, "기밀 프로젝트 리딩", "", emptyList(), Sensitivity.CONFIDENTIAL, 60),
                ),
            ),
        )

    private fun reply(vararg items: String) =
        ModelOutcome.Completed(
            """{"explanations":[${items.joinToString(",")}]}""",
            "claude-opus-5",
            ModelUsage(800, 200, 0, 0),
            false,
        )

    private fun item(
        r: UUID,
        c: UUID,
        reason: String = "SaaS 제품 기획을 직접 수행한 사실이 요구사항을 뒷받침합니다.",
    ) =
        """{"requirementId":"$r","claimId":"$c","reason":"$reason","matchedRequirementPhrase":"SaaS 제품 기획","matchedEvidencePhrase":"SaaS 온보딩 재설계"}"""

    @Test
    fun `explanations are kept only for offered pairs whose claim reached the model`() {
        fake.enqueue(
            reply(
                item(req, claimA),
                item(req, claimSecret),
                item(UUID.randomUUID(), claimA),
                item(req, claimA, reason = ""),
            ),
        )

        val result = explainer.explain(workspace, requirements())

        assertThat(result.explanations).hasSize(1)
        assertThat(result.explanations.single().matchedEvidencePhrase).isEqualTo("SaaS 온보딩 재설계")
        assertThat(result.dropped)
            .hasSize(3)
            .anyMatch {
                it.contains("excluded from context")
            }.anyMatch { it.startsWith("unoffered") }
        // The confidential claim never left the process.
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).containsExactly("requirements", "claim:$claimA")
        assertThat(fake.requests.single().instruction).contains("claimId=$claimA (score 82)")
    }
}
