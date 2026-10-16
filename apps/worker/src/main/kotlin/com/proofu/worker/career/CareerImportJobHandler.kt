package com.proofu.worker.career

import com.proofu.ai.extraction.CareerImportExtractor
import com.proofu.domain.common.WorkspaceId
import com.proofu.worker.jobs.AiFailures
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * F13: proposes career entries from a pasted resume. Nothing is written — the proposals travel
 * back in the job result and the user creates the ones they keep through the ordinary endpoint
 * (docs/ai/ai-feature-spec.md §이력서 가져오기), the same arrangement as 문장 개선.
 */
@Component
class CareerImportJobHandler(
    private val extractor: CareerImportExtractor,
    private val mapper: ObjectMapper,
) : JobHandler {
    private val log = LoggerFactory.getLogger(CareerImportJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val text = payload.get("text")?.asString().orEmpty()
        if (text.isBlank()) throw JobFailure("VALIDATION_FAILED", "empty resume text", retryable = false)

        val result = AiFailures.guard { extractor.propose(WorkspaceId(job.workspaceId), text, job.id) }

        // Never the text, never a quote: this line is about how the run went (privacy §12.2).
        log.info(
            "career.import proposals={} dropped={} redacted={} withoutQuote={} cost={}µ$",
            result.proposals.size,
            result.dropped.size,
            result.redacted,
            result.proposals.count { it.warning != null },
            result.costMicros,
        )
        return mapper.writeValueAsString(
            mapOf(
                "proposals" to
                    result.proposals.map {
                        mapOf(
                            "type" to it.type.name,
                            "title" to it.title,
                            "organization" to it.organization,
                            "location" to it.location,
                            "description" to it.description,
                            "startDate" to it.startDate.toString(),
                            "endDate" to it.endDate?.toString(),
                            "confidence" to it.confidence,
                            "quote" to it.quote,
                            "warning" to it.warning,
                        )
                    },
                "dropped" to result.dropped,
                "redacted" to result.redacted,
                "executionId" to result.executionId.toString(),
                "costMicros" to result.costMicros,
            ),
        )
    }

    companion object {
        /** Must match apps/api JobTypes.CAREER_IMPORT. */
        const val TYPE = "career.import"
    }
}
