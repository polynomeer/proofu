package com.proofu.worker.analysis

import com.proofu.ai.extraction.RequirementExtractor
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.SnapshotSource
import com.proofu.worker.jobs.AiFailures
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * F04: extracts requirements from a posting snapshot and stores them as AI drafts for review.
 * Idempotent per run: AI drafts nobody has reviewed yet are replaced; approved, rejected and
 * user-written requirements are never touched.
 */
@Component
class PostingAnalysisJobHandler(
    private val extractor: RequirementExtractor,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
) : JobHandler {
    private val log = LoggerFactory.getLogger(PostingAnalysisJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val snapshotId = UUID.fromString(payload.get("snapshotId").asString())
        val snapshot =
            loadSnapshot(snapshotId, job.workspaceId)
                ?: throw JobFailure("SNAPSHOT_NOT_FOUND", "snapshot $snapshotId", retryable = false)

        val result =
            AiFailures.guard {
                extractor.extract(WorkspaceId(job.workspaceId), snapshot, job.id)
            }

        val stored: Int =
            tx.execute<Int> {
                val replaced =
                    jdbc.update(
                        "update requirements set deleted_at = now(), version = version + 1 where snapshot_id = ? and origin = 'AI' and status = 'DRAFT' and deleted_at is null",
                        snapshotId,
                    )
                var order =
                    jdbc.queryForObject(
                        "select coalesce(max(sort_order), 0) from requirements where snapshot_id = ?",
                        Int::class.java,
                        snapshotId,
                    )
                        ?: 0
                result.items.forEach { item ->
                    val r = item.requirement
                    jdbc.update(
                        """
                        insert into requirements
                          (id, snapshot_id, category, text, span_start, span_end, confidence, origin, status, sort_order, version)
                        values (?, ?, ?, ?, ?, ?, ?, 'AI', 'DRAFT', ?, 1)
                        """.trimIndent(),
                        r.id.value,
                        snapshotId,
                        r.category.name,
                        r.text,
                        r.sourceSpan?.start,
                        r.sourceSpan?.end,
                        BigDecimal.valueOf(r.confidence.value).setScale(2, RoundingMode.HALF_UP),
                        ++order,
                    )
                }
                replaced
            }

        val withoutSpan = result.items.count { it.requirement.sourceSpan == null }
        log.info(
            "posting.analysis snapshot={} extracted={} withoutSpan={} dropped={} replaced={} cost={}µ$",
            snapshotId,
            result.items.size,
            withoutSpan,
            result.dropped.size,
            stored,
            result.costMicros,
        )
        return mapper.writeValueAsString(
            mapOf(
                "snapshotId" to snapshotId.toString(),
                "extracted" to result.items.size,
                "withoutSpan" to withoutSpan,
                "replacedDrafts" to stored,
                "dropped" to result.dropped,
                "warnings" to result.items.mapNotNull { it.warning },
                "executionId" to result.executionId.toString(),
                "costMicros" to result.costMicros,
            ),
        )
    }

    private fun loadSnapshot(
        id: UUID,
        workspaceId: UUID,
    ): JobPostingSnapshot? =
        jdbc
            .query(
                """
                select s.id, s.posting_id, s.source, s.raw_text, s.content_hash, s.captured_at, s.source_url
                from job_posting_snapshots s join job_postings p on p.id = s.posting_id
                where s.id = ? and p.workspace_id = ? and p.deleted_at is null
                """.trimIndent(),
                { rs, _ ->
                    JobPostingSnapshot(
                        id = JobPostingSnapshotId(rs.getObject("id", UUID::class.java)),
                        postingId = JobPostingId(rs.getObject("posting_id", UUID::class.java)),
                        source = SnapshotSource.valueOf(rs.getString("source")),
                        rawText = rs.getString("raw_text"),
                        contentHash = rs.getString("content_hash"),
                        capturedAt = rs.getTimestamp("captured_at").toInstant(),
                        sourceUrl = rs.getString("source_url"),
                    )
                },
                id,
                workspaceId,
            ).firstOrNull()

    companion object {
        /** Must match apps/api JobTypes.POSTING_ANALYSIS. */
        const val TYPE = "posting.analysis"
    }
}
