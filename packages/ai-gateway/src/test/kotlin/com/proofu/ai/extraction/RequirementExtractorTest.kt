package com.proofu.ai.extraction

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.RequirementCategory
import com.proofu.domain.jobs.RequirementOrigin
import com.proofu.domain.jobs.RequirementStatus
import com.proofu.domain.jobs.SnapshotSource
import com.proofu.domain.jobs.SourceSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RequirementExtractorTest {
    private val fake = FakeModelClient()
    private val ids = Uuid7IdGenerator()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "fake"),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = { },
            clock = Clock.systemUTC(),
            ids = ids,
            client = fake,
        )
    private val extractor = RequirementExtractor(gateway, ids)
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val snapshot =
        JobPostingSnapshot.capture(
            JobPostingSnapshotId(UUID.randomUUID()),
            JobPostingId(UUID.randomUUID()),
            SnapshotSource.MANUAL_TEXT,
            "[자격 요건]\n- 경력 5년 이상\n- SaaS 제품   기획 경험\n\n[우대 사항]\n- 영어 커뮤니케이션",
            Instant.EPOCH,
        )
    private val sourceId get() = "snapshot:${snapshot.id.value}"

    private fun reply(vararg items: String) =
        ModelOutcome.Completed(
            """{"items":[${items.joinToString(",")}]}""",
            "claude-opus-5",
            ModelUsage(500, 100, 0, 0),
            false,
        )

    private fun item(
        category: String,
        text: String,
        quote: String,
        confidence: Double = 0.9,
        source: String = sourceId,
    ) = """{"category":"$category","text":"$text","quote":"$quote","confidence":$confidence,"sourceId":"$source"}"""

    @Test
    fun `quotes become spans and every item is an AI draft`() {
        fake.enqueue(reply(item("REQUIRED", "경력 5년 이상", "경력 5년 이상"), item("PREFERRED", "영어 커뮤니케이션 가능", "영어 커뮤니케이션")))

        val result = extractor.extract(workspace, snapshot)

        assertThat(result.items).hasSize(2)
        val first = result.items[0].requirement
        assertThat(first.status).isEqualTo(RequirementStatus.DRAFT)
        assertThat(first.origin).isEqualTo(RequirementOrigin.AI)
        assertThat(first.category).isEqualTo(RequirementCategory.REQUIRED)
        assertThat(snapshot.excerpt(first.sourceSpan!!)).isEqualTo("경력 5년 이상")
        assertThat(result.items.map { it.warning }).containsOnlyNulls()
        assertThat(
            fake.requests
                .single()
                .documents
                .single()
                .sourceId,
        ).isEqualTo(sourceId)
    }

    @Test
    fun `whitespace differences in a quote are tolerated, unknown quotes keep no span`() {
        fake.enqueue(reply(item("REQUIRED", "SaaS 제품 기획 경험", "SaaS 제품 기획 경험"), item("SKILL", "Kotlin", "Kotlin 5년")))

        val result = extractor.extract(workspace, snapshot)

        assertThat(snapshot.excerpt(result.items[0].requirement.sourceSpan!!)).isEqualTo("SaaS 제품   기획 경험")
        assertThat(result.items[1].requirement.sourceSpan).isNull()
        assertThat(result.items[1].warning).contains("찾지 못했습니다")
    }

    @Test
    fun `items citing an unknown source, empty text or duplicates are dropped`() {
        fake.enqueue(
            reply(
                item("REQUIRED", "경력 5년 이상", "경력 5년 이상"),
                item("REQUIRED", "경력 5년 이상", "경력 5년 이상"),
                item("REQUIRED", "  ", "x"),
                item("REQUIRED", "다른 문서", "x", source = "snapshot:other"),
            ),
        )

        val result = extractor.extract(workspace, snapshot)

        assertThat(result.items).hasSize(1)
        assertThat(result.dropped)
            .hasSize(3)
            .anyMatch {
                it.startsWith("duplicate")
            }.anyMatch { it.startsWith("unknown sourceId") }
    }

    @Test
    fun `confidence outside the unit interval is clamped`() {
        fake.enqueue(reply(item("REQUIRED", "경력 5년 이상", "경력 5년 이상", confidence = 1.7)))
        assertThat(
            extractor
                .extract(workspace, snapshot)
                .items
                .single()
                .requirement.confidence.value,
        ).isEqualTo(1.0)
    }

    @Test
    fun `locate handles exact, collapsed and missing quotes`() {
        val text = "a  b\nc d"
        assertThat(RequirementExtractor.locate(text, "b\nc")).isEqualTo(SourceSpan(3, 6))
        assertThat(RequirementExtractor.locate(text, "bc")).isEqualTo(SourceSpan(3, 6))
        assertThat(RequirementExtractor.locate(text, "zzz")).isNull()
        assertThat(RequirementExtractor.locate(text, "   ")).isNull()
    }
}
