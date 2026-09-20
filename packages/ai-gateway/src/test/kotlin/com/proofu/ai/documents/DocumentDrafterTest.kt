package com.proofu.ai.documents

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.evidence.ClaimStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class DocumentDrafterTest {
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
    private val drafter = DocumentDrafter(gateway)
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val req = UUID.randomUUID()
    private val supported = UUID.randomUUID()
    private val evidence = UUID.randomUUID()
    private val unsupported = UUID.randomUUID()
    private val secret = UUID.randomUUID()

    private fun request() =
        DraftRequest(
            template = DocumentTemplate.latest(DocumentType.COVER_LETTER),
            language = "ko",
            posting = PostingForDraft("프로덕트 매니저", "예시 주식회사"),
            requirements = listOf(RequirementForDraft(req, "REQUIRED", "SaaS 제품 기획 경험")),
            claims =
                listOf(
                    ClaimForDraft(
                        supported,
                        "SaaS 온보딩 재설계를 주도",
                        "활성화율 40% 개선",
                        ClaimStatus.SUPPORTED,
                        listOf(EvidenceForDraft(evidence, "Q2 대시보드")),
                        setOf(req),
                        Sensitivity.INTERNAL,
                    ),
                    ClaimForDraft(
                        unsupported,
                        "분기 로드맵 수립",
                        "",
                        ClaimStatus.UNSUPPORTED,
                        emptyList(),
                        setOf(req),
                        Sensitivity.INTERNAL,
                    ),
                    ClaimForDraft(
                        secret,
                        "기밀 프로젝트 리딩",
                        "",
                        ClaimStatus.SUPPORTED,
                        emptyList(),
                        setOf(req),
                        Sensitivity.CONFIDENTIAL,
                    ),
                ),
        )

    private fun block(
        section: String,
        text: String,
        claims: List<UUID> = emptyList(),
        evidence: List<UUID> = emptyList(),
        requirements: List<UUID> = emptyList(),
        certainty: String = "SUPPORTED",
    ) = """{"section":"$section","text":"$text","claimRefs":[${claims.joinToString(",") { "\"$it\"" }}],""" +
        """"evidenceRefs":[${evidence.joinToString(",") { "\"$it\"" }}],""" +
        """"requirementRefs":[${requirements.joinToString(",") { "\"$it\"" }}],"certainty":"$certainty"}"""

    @Test
    fun `blocks are numbered per section, whitelisted and capped by claim evidence`() {
        fake.enqueue(
            ModelOutcome.Completed(
                """{"blocks":[
                  ${block("motivation", "SaaS 제품을 만들고 싶습니다.", certainty = "UNSUPPORTED")},
                  ${block(
                    "experience",
                    "온보딩 재설계로 활성화율을 40% 개선했습니다.",
                    listOf(supported),
                    listOf(evidence),
                    listOf(req),
                )},
                  ${block("experience", "분기 로드맵을 수립했습니다.", listOf(unsupported), requirements = listOf(req))},
                  ${block("experience", "기밀 프로젝트를 이끌었습니다.", listOf(secret))},
                  ${block("experience", "지어낸 사실입니다.", listOf(UUID.randomUUID()))},
                  ${block("awards", "수상 경력", listOf(supported))},
                  ${block("contribution", "  ")}
                ]}""",
                "claude-opus-5",
                ModelUsage(2_000, 600, 0, 0),
                false,
            ),
        )

        val result = drafter.draft(workspace, request())

        val blocks = result.output.blocks
        assertThat(blocks.map { it.blockId }).containsExactly("motivation-1", "experience-1", "experience-2")
        assertThat(blocks[0].certainty).isEqualTo(Certainty.UNSUPPORTED)
        assertThat(blocks[1].certainty).isEqualTo(Certainty.SUPPORTED)
        assertThat(blocks[1].evidenceRefs).extracting("value").containsExactly(evidence)
        assertThat(blocks[2].certainty).isEqualTo(Certainty.INFERRED)
        assertThat(blocks[2].warnings).hasSize(1)
        assertThat(blocks).allMatch { !it.approvedByUser }
        assertThat(result.dropped).hasSize(4)
        assertThat(
            result.output.blocksPendingApproval().map { it.blockId },
        ).containsExactly("motivation-1", "experience-2")
        // The confidential claim never left the process, so a block citing it is a hallucinated reference.
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).containsExactly("requirements", "claim:$supported", "claim:$unsupported")
        assertThat(fake.requests.single().instruction).contains("- section=motivation")
    }

    @Test
    fun `fake provider produces a draft the approval flow can exercise`() {
        val result = drafter.draft(workspace, request())

        assertThat(
            result.output.blocks.map { it.blockId },
        ).containsExactly("motivation-1", "experience-1", "experience-2")
        assertThat(result.output.blocks[1].claimRefs).containsExactly(ClaimId(supported))
        assertThat(result.output.blocks[1].certainty).isEqualTo(Certainty.SUPPORTED)
        assertThat(result.output.blocks[2].certainty).isEqualTo(Certainty.INFERRED)
    }
}
