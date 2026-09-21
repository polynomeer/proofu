package com.proofu.ai.eval

import com.proofu.ai.AiExecutionRecord
import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.documents.ClaimForDraft
import com.proofu.ai.documents.DocumentDrafter
import com.proofu.ai.documents.DraftRequest
import com.proofu.ai.documents.EvidenceForDraft
import com.proofu.ai.documents.PostingForDraft
import com.proofu.ai.documents.RequirementForDraft
import com.proofu.ai.matching.CandidateToExplain
import com.proofu.ai.matching.MatchExplainer
import com.proofu.ai.matching.RequirementToExplain
import com.proofu.ai.revision.SentenceReviser
import com.proofu.ai.revision.SentenceToRevise
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.RevisionGuard
import com.proofu.domain.documents.RevisionMode
import com.proofu.domain.evidence.ClaimStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Live check of the three prompts that follow extraction (docs/ai/evaluation.md). Opt-in:
 * needs ANTHROPIC_API_KEY and costs a few cents per run. Everything the model returns is
 * printed for a human to read; the assertions cover only what a machine can judge —
 * grounding guards, structure and the revision guard.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LivePromptEvalTest {
    private val records = mutableListOf<AiExecutionRecord>()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "anthropic", apiKey = System.getenv("ANTHROPIC_API_KEY")),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = { records += it },
            clock = Clock.systemUTC(),
            ids = Uuid7IdGenerator(),
        )
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `explanation, drafting and revision stay grounded on the synthetic scenarios`() {
        val fixtures = Path.of("../../fixtures/ai/scenarios").listDirectoryEntries("*.json")
        assertThat(fixtures).isNotEmpty
        val soft = SoftAssertions()
        for (file in fixtures) {
            val f = mapper.readTree(Files.readString(file))
            println("==== ${file.name}")
            explain(f, soft)
            draft(f, soft)
            revise(f, soft)
        }
        val cost = records.sumOf { it.costMicros ?: 0L }
        println(
            "---- ${records.size} executions, total cost ${"%.4f".format(
                cost / 1_000_000.0,
            )} USD, statuses=${records.map { it.status }}",
        )
        soft.assertThat(records.map { it.status.name }).allMatch { it == "SUCCEEDED" }
        soft.assertAll()
    }

    private fun JsonNode.items(): List<JsonNode> = buildList { for (n in this@items) add(n) }

    private fun explain(
        f: JsonNode,
        soft: SoftAssertions,
    ) {
        val claims = f.get("claims").items()
        val requirements =
            f
                .get("requirements")
                .items()
                .map { r ->
                    val id = UUID.fromString(r.get("id").asString())
                    val candidates =
                        claims
                            .filter { c -> c.get("requirementIds").items().any { it.asString() == id.toString() } }
                            .map { c ->
                                CandidateToExplain(
                                    UUID.fromString(c.get("id").asString()),
                                    c.get("text").asString(),
                                    c.get("sourceText").asString(),
                                    c.get("evidence").items().map { it.get("title").asString() },
                                    Sensitivity.INTERNAL,
                                    c.get("score").asInt(),
                                )
                            }
                    RequirementToExplain(id, r.get("category").asString(), r.get("text").asString(), candidates)
                }.filter { r -> r.candidates.isNotEmpty() }
        val offered = requirements.sumOf { r -> r.candidates.size }
        val result = MatchExplainer(gateway).explain(workspace, requirements)
        println("-- explain: ${result.explanations.size}/$offered explained, dropped=${result.dropped}")
        result.explanations.forEach {
            println("  ${it.requirementId.toString().takeLast(2)}/${it.claimId.toString().takeLast(2)}: ${it.reason}")
            println("     req=\"${it.matchedRequirementPhrase}\" ev=\"${it.matchedEvidencePhrase}\"")
        }
        soft.assertThat(result.explanations).describedAs("one explanation per offered pair").hasSize(offered)
        soft.assertThat(result.dropped).describedAs("nothing unoffered or empty").isEmpty()
        soft.assertThat(result.explanations.map { it.reason }).noneMatch { it.contains("합격") }
    }

    private fun draft(
        f: JsonNode,
        soft: SoftAssertions,
    ) {
        val language = f.get("language")?.asString() ?: "ko"
        val type = f.get("documentType")?.asString()?.let(DocumentType::valueOf) ?: DocumentType.COVER_LETTER
        val template = DocumentTemplate.latest(type)
        val request =
            DraftRequest(
                template = template,
                language = language,
                posting =
                    PostingForDraft(
                        f.get("posting").get("title").asString(),
                        f.get("posting").get("company").asString(),
                    ),
                requirements =
                    f.get("requirements").items().map {
                        RequirementForDraft(
                            UUID.fromString(it.get("id").asString()),
                            it.get("category").asString(),
                            it.get("text").asString(),
                        )
                    },
                claims =
                    f.get("claims").items().map { c ->
                        ClaimForDraft(
                            id = UUID.fromString(c.get("id").asString()),
                            text = c.get("text").asString(),
                            sourceText = c.get("sourceText").asString(),
                            status = ClaimStatus.valueOf(c.get("status").asString()),
                            evidence =
                                c.get("evidence").items().map {
                                    EvidenceForDraft(
                                        UUID.fromString(it.get("id").asString()),
                                        it.get("title").asString(),
                                    )
                                },
                            requirementIds =
                                c
                                    .get(
                                        "requirementIds",
                                    ).items()
                                    .map { UUID.fromString(it.asString()) }
                                    .toSet(),
                            sensitivity = Sensitivity.INTERNAL,
                        )
                    },
            )
        val result = DocumentDrafter(gateway).draft(workspace, request)
        println(
            "-- draft: ${result.output.blocks.size} blocks, dropped=${result.dropped}, truncated=${result.truncated}",
        )
        result.output.blocks.forEach { b ->
            println(
                "  [${b.blockId}] ${b.certainty} claims=${b.claimRefs.size} ev=${b.evidenceRefs.size} req=${b.requirementRefs.size}",
            )
            println("     ${b.text}")
            b.warnings.forEach { println("     ! $it") }
        }
        val sections =
            result.output.blocks
                .map { template.sectionOf(it.blockId)?.id }
                .toSet()
        soft.assertThat(sections).describedAs("at least two sections filled").hasSizeGreaterThanOrEqualTo(2)
        soft.assertThat(result.dropped).describedAs("no block cited something outside the context").isEmpty()
        soft
            .assertThat(
                result.output.blocks.flatMap {
                    it.warnings
                },
            ).describedAs("model never claimed more certainty than the claims allow")
            .isEmpty()
        soft.assertThat(result.truncated).isFalse()
        // Figures in the draft must come from the claims (dates may be respelled; nothing new).
        val allowed =
            f
                .get("claims")
                .items()
                .flatMap { RevisionGuard.numbers(it.get("text").asString() + " " + it.get("sourceText").asString()) }
                .toSet()
        val invented =
            result.output.blocks
                .flatMap { RevisionGuard.newNumbers(it.text, allowed) }
                .distinct()
        soft.assertThat(invented).describedAs("numbers not present in any claim").isEmpty()
        if (language == "en") {
            soft
                .assertThat(result.output.blocks.map { it.text })
                .describedAs("English draft has no Hangul")
                .noneMatch { t -> t.any { ch -> ch in '가'..'힣' } }
        }
        // The body speaks to a recruiter; evidence bookkeeping stays in the refs.
        soft
            .assertThat(result.output.blocks.map { it.text })
            .describedAs("no evidence meta-commentary in the text")
            .noneMatch { it.contains("증빙") || it.contains("확인할 수 있습니다") || it.contains("Evidence") }
    }

    private fun revise(
        f: JsonNode,
        soft: SoftAssertions,
    ) {
        val spec = f.get("revision")
        val claim = f.get("claims").items().first { it.get("id").asString() == spec.get("claimId").asString() }
        val sentence =
            SentenceToRevise(
                blockId = "experience-1",
                text = claim.get("text").asString(),
                facts = listOf(claim.get("sourceText").asString()),
                mode = RevisionMode.valueOf(spec.get("mode").asString()),
                language = f.get("language")?.asString() ?: "ko",
            )
        val result = SentenceReviser(gateway).revise(workspace, sentence)
        println("-- revise (${sentence.mode}): ${result.revised ?: "REJECTED ${result.rejected}"}")
        result.changes.forEach { println("     · $it") }
        soft.assertThat(result.revised).describedAs("revision passed the guard").isNotNull()
        val kept = RevisionGuard.numbers(result.revised ?: "")
        for (n in spec.get(
            "mustKeepNumbers",
        )) {
            soft.assertThat(kept).describedAs("keeps number ${n.asString()}").contains(n.asString())
        }
        if (sentence.mode == RevisionMode.SHORTEN && result.revised != null) {
            soft
                .assertThat(
                    result.revised.length,
                ).describedAs("shorter than the original")
                .isLessThanOrEqualTo(sentence.text.length)
        }
    }
}
