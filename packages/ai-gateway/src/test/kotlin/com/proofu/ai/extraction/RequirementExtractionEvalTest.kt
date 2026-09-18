package com.proofu.ai.extraction

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.SnapshotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Fixed evaluation set for F04 (docs/ai/evaluation.md). Opt-in: needs ANTHROPIC_API_KEY.
 * Pass criteria per fixture: every expected quote is located (recall ≥ 90% overall),
 * nothing from `mustNotContain` is extracted, and every extracted item has a resolvable span.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RequirementExtractionEvalTest {
    private val ids = Uuid7IdGenerator()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "anthropic", apiKey = System.getenv("ANTHROPIC_API_KEY")),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = {
                println(
                    "execution ${it.status} tokens=${it.inputTokens}/${it.outputTokens} cost=${it.costMicros}µ$",
                )
            },
            clock = Clock.systemUTC(),
            ids = ids,
        )
    private val extractor = RequirementExtractor(gateway, ids)
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `synthetic postings meet the extraction bar`() {
        val dir = Path.of("../../fixtures/ai/postings")
        val fixtures = dir.listDirectoryEntries("*.json")
        assertThat(fixtures).isNotEmpty
        val soft = SoftAssertions()
        var expectedTotal = 0
        var found = 0

        for (file in fixtures) {
            val fixture = mapper.readTree(Files.readString(file))
            val snapshot =
                JobPostingSnapshot.capture(
                    JobPostingSnapshotId(UUID.randomUUID()),
                    JobPostingId(UUID.randomUUID()),
                    SnapshotSource.MANUAL_TEXT,
                    fixture.get("text").asString(),
                    Instant.now(),
                )
            val result = extractor.extract(WorkspaceId(UUID.randomUUID()), snapshot)
            println("== ${file.name}: ${result.items.size} items, dropped=${result.dropped}")
            result.items.forEach {
                println("  [${it.requirement.category}] ${it.requirement.text}  <- \"${it.quote}\" ${it.warning ?: ""}")
            }

            val quotes = result.items.mapNotNull { it.requirement.sourceSpan?.let(snapshot::excerpt) }
            for (expected in fixture.get("expected")) {
                expectedTotal++
                val quote = expected.get("quote").asString()
                if (quotes.any { it.contains(quote) || quote.contains(it) }) found++ else println("  MISSING: $quote")
            }
            for (forbidden in fixture.get("mustNotContain")) {
                val f = forbidden.asString()
                soft.assertThat(result.items.map { it.requirement.text + it.quote }).noneMatch { it.contains(f) }
            }
            soft.assertThat(result.items.map { it.warning }).describedAs("${file.name} spans").containsOnlyNulls()
        }
        val recall = found.toDouble() / expectedTotal
        println("recall = $found/$expectedTotal = ${"%.2f".format(recall)}")
        soft.assertThat(recall).isGreaterThanOrEqualTo(0.9)
        soft.assertAll()
    }
}
