package com.proofu.ai.revision

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.RevisionMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class SentenceReviserTest {
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
    private val reviser = SentenceReviser(gateway)
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val sentence =
        SentenceToRevise(
            "experience-1",
            "온보딩 플로우를 5단계에서 2단계로 축소해서 활성화율을 40% 정도 개선을 했습니다.",
            listOf("가입 후 7일 활성화율 상승 (40%)"),
            RevisionMode.SHORTEN,
        )

    private fun reply(
        revised: String,
        vararg changes: String,
    ) = ModelOutcome.Completed(
        """{"revised":${'"'}$revised${'"'},"changes":[${changes.joinToString(",") { "\"$it\"" }}]}""",
        "claude-opus-5",
        ModelUsage(300, 80, 0, 0),
        false,
    )

    @Test
    fun `a faithful rewrite is returned with its change notes`() {
        fake.enqueue(reply("온보딩을 5→2단계로 줄여 7일 활성화율을 40% 개선했습니다.", "군더더기 제거"))
        val result = reviser.revise(workspace, sentence)
        assertThat(result.revised).isEqualTo("온보딩을 5→2단계로 줄여 7일 활성화율을 40% 개선했습니다.")
        assertThat(result.changes).containsExactly("군더더기 제거")
        assertThat(result.rejected).isEmpty()
        assertThat(
            fake.requests
                .single()
                .documents
                .map { it.sourceId },
        ).containsExactly("block:experience-1", "fact:0")
        assertThat(fake.requests.single().instruction).contains("SHORTEN")
    }

    @Test
    fun `a rewrite that invents a number is refused by the guard`() {
        fake.enqueue(reply("활성화율을 45% 개선했습니다.", "수치 보정"))
        val result = reviser.revise(workspace, sentence)
        assertThat(result.revised).isNull()
        assertThat(result.rejected).anyMatch { it.contains("45") }
    }

    @Test
    fun `fake provider yields a proposal that passes the guard`() {
        val result = reviser.revise(workspace, sentence)
        assertThat(result.revised).endsWith("(가짜 제공자 제안)")
        assertThat(result.rejected).isEmpty()
    }
}
