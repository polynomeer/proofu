package com.proofu.worker.imports

import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.imports.ImportItem
import com.proofu.domain.imports.ImportItemStatus
import com.proofu.domain.imports.ImportItemType
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.SnapshotSource
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * F21 1단계: creates what the user picked out of a plan
 * (docs/product/personal-archive-import.md §흐름).
 *
 * **The bundle comes with the request, not from the ledger.** Nothing of it is stored between
 * planning and applying — that is the point of the ledger being only keys, hashes and verdicts —
 * so applying re-sends the same bundle. Each picked item is hashed again and must match what the
 * plan recorded; an item whose content moved in between is refused rather than applied from a
 * stale preview.
 *
 * Only `NEW` items are applied. Updating something already imported is its own decision and
 * belongs with the screen that shows the difference.
 *
 * Postings go first: an application needs a snapshot, and a snapshot needs text. The text is
 * whatever the archive had, captured under [SnapshotSource.IMPORTED] so a reader can tell it is as
 * complete as that archive was. The server never writes posting text of its own.
 */
@Component
class ImportApplyJobHandler(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val hashes: ImportHashing,
) : JobHandler {
    private val log = LoggerFactory.getLogger(ImportApplyJobHandler::class.java)

    override val type = TYPE

    private data class Verdict(
        val id: UUID,
        val status: ImportItemStatus,
        val contentHash: String,
        /** Set once this item produced a row; re-applying must not make a second one. */
        val alreadyApplied: Boolean,
    )

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val runId =
            payload.get("runId")?.asString()?.let(UUID::fromString)
                ?: throw JobFailure("VALIDATION_FAILED", "no run id", retryable = false)
        val picked =
            payload
                .get("sourceKeys")
                ?.toList()
                ?.map { it.asString() }
                ?.toSet()
                .orEmpty()
        if (picked.isEmpty()) throw JobFailure("VALIDATION_FAILED", "nothing was picked", retryable = false)

        val bundle = hashes.itemsOf(payload)
        val verdicts = verdictsOf(runId, job.workspaceId)
        if (verdicts.isEmpty()) throw JobFailure("NOT_FOUND", "no such run in this workspace", retryable = false)

        val applied = mutableMapOf<String, UUID>()
        val skipped = mutableMapOf<String, String>()
        val snapshots = mutableMapOf<String, UUID>()
        val now = Instant.now(clock)

        tx.execute {
            // Postings first, so an application can point at the snapshot its posting produced.
            val order = bundle.filter { it.sourceKey in picked }.sortedBy { it.type != ImportItemType.POSTING }
            order.forEach { item ->
                val verdict = verdicts[item.sourceKey]
                when {
                    verdict == null -> skipped[item.sourceKey] = "이 계획에 없는 항목입니다"
                    verdict.alreadyApplied -> skipped[item.sourceKey] = "이미 들여왔습니다"
                    verdict.status != ImportItemStatus.NEW ->
                        skipped[item.sourceKey] = "이 단계는 새 항목만 들입니다 (현재 ${verdict.status.name})"

                    verdict.contentHash != item.contentHash ->
                        skipped[item.sourceKey] = "계획을 세운 뒤 내용이 바뀌었습니다. 다시 계획하세요"

                    else -> apply(item, verdict, job.workspaceId, now, applied, skipped, snapshots, bundle)
                }
            }
            if (applied.isNotEmpty()) {
                jdbc.update(
                    "update import_runs set status = 'APPLIED', applied_at = ? where id = ?",
                    OffsetDateTime.ofInstant(now, clock.zone),
                    runId,
                )
            }
        }

        // Counts and keys only: a field value can carry a company or a person's name.
        log.info("import.apply run={} applied={} skipped={}", runId, applied.size, skipped.size)
        return mapper.writeValueAsString(
            mapOf(
                "runId" to runId.toString(),
                "applied" to applied.mapValues { it.value.toString() },
                "skipped" to skipped,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun apply(
        item: ImportItem,
        verdict: Verdict,
        workspaceId: UUID,
        now: Instant,
        applied: MutableMap<String, UUID>,
        skipped: MutableMap<String, String>,
        snapshots: MutableMap<String, UUID>,
        bundle: List<ImportItem>,
    ) {
        when (item.type) {
            ImportItemType.POSTING -> {
                val snapshotId = applyPosting(item, workspaceId)
                snapshots[item.sourceKey] = snapshotId
                mark(verdict.id, "posting_snapshot", snapshotId, now)
                applied[item.sourceKey] = snapshotId
            }

            ImportItemType.APPLICATION -> {
                val postingKey = item.links["posting"].orEmpty().firstOrNull()
                val snapshotId = postingKey?.let { snapshots[it] } ?: appliedSnapshotOf(workspaceId, postingKey)
                if (snapshotId == null) {
                    skipped[item.sourceKey] = "가리키는 공고가 아직 들어오지 않았습니다. 공고를 함께 고르세요"
                    return
                }
                val applicationId = applyApplication(item, workspaceId, snapshotId)
                mark(verdict.id, "application", applicationId, now)
                applied[item.sourceKey] = applicationId
            }

            else -> {
                check(bundle.isNotEmpty())
                skipped[item.sourceKey] = "이 단계에서는 ${item.type.name.lowercase()} 항목을 아직 들이지 않습니다"
            }
        }
    }

    private fun applyPosting(
        item: ImportItem,
        workspaceId: UUID,
    ): UUID {
        val postingId = UUID.randomUUID()
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title, canonical_url) values (?, ?, ?, ?, ?)",
            postingId,
            workspaceId,
            item.field("company").orEmpty().take(MAX_NAME),
            item.field("roleTitle")?.take(MAX_NAME),
            item.field("canonicalUrl"),
        )
        // Whatever text the archive had. A posting with nothing but a company line still carries
        // that line — the domain refuses blank text, and the server writes none of its own.
        val text =
            item.content.ifBlank {
                listOfNotNull(item.field("company"), item.field("roleTitle")).joinToString(" ")
            }
        val snapshot =
            JobPostingSnapshot.capture(
                id = JobPostingSnapshotId(UUID.randomUUID()),
                postingId = JobPostingId(postingId),
                source = SnapshotSource.IMPORTED,
                text = text,
                capturedAt = Instant.now(clock),
                sourceUrl = item.field("canonicalUrl"),
            )
        jdbc.update(
            """
            insert into job_posting_snapshots (id, posting_id, source, source_url, raw_text, content_hash, captured_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            snapshot.id.value,
            postingId,
            snapshot.source.name,
            snapshot.sourceUrl,
            snapshot.rawText,
            snapshot.contentHash,
            OffsetDateTime.ofInstant(snapshot.capturedAt, clock.zone),
        )
        return snapshot.id.value
    }

    /**
     * The application is created at the status the archive recorded, not walked there through
     * transitions: it really was submitted, and writing a trail of transitions that never happened
     * here would be a worse record than none (docs/domain/application-lifecycle.md).
     */
    private fun applyApplication(
        item: ImportItem,
        workspaceId: UUID,
        snapshotId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into applications (id, workspace_id, snapshot_id, company, role_title, status, deadline_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            workspaceId,
            snapshotId,
            item.field("company").orEmpty().take(MAX_NAME),
            item.field("roleTitle").orEmpty().take(MAX_NAME),
            item.field("status") ?: "INTERESTED",
            item.field("deadlineAt")?.let { OffsetDateTime.ofInstant(parseDate(it), clock.zone) },
        )
        return id
    }

    private fun parseDate(value: String): Instant =
        runCatching { LocalDate.parse(value).atStartOfDay(clock.zone).toInstant() }
            .getOrElse { throw JobFailure("VALIDATION_FAILED", "bad date: $value", retryable = false) }

    /** A posting that an earlier run already applied, so a later bundle can point at it. */
    private fun appliedSnapshotOf(
        workspaceId: UUID,
        postingKey: String?,
    ): UUID? {
        if (postingKey == null) return null
        return jdbc
            .query(
                """
                select target_id from import_items
                where workspace_id = ? and source_key = ? and target_type = 'posting_snapshot'
                  and applied_at is not null
                order by applied_at desc limit 1
                """.trimIndent(),
                { rs, _ -> rs.getObject("target_id", UUID::class.java) },
                workspaceId,
                postingKey,
            ).firstOrNull()
    }

    private fun mark(
        itemId: UUID,
        targetType: String,
        targetId: UUID,
        now: Instant,
    ) {
        jdbc.update(
            "update import_items set target_type = ?, target_id = ?, applied_at = ? where id = ?",
            targetType,
            targetId,
            OffsetDateTime.ofInstant(now, clock.zone),
            itemId,
        )
    }

    private fun verdictsOf(
        runId: UUID,
        workspaceId: UUID,
    ): Map<String, Verdict> =
        jdbc
            .query(
                """
                select id, source_key, status, content_hash, applied_at
                from import_items where run_id = ? and workspace_id = ?
                """.trimIndent(),
                { rs, _ ->
                    rs.getString("source_key") to
                        Verdict(
                            rs.getObject("id", UUID::class.java),
                            ImportItemStatus.valueOf(rs.getString("status")),
                            rs.getString("content_hash"),
                            rs.getObject("applied_at", OffsetDateTime::class.java) != null,
                        )
                },
                runId,
                workspaceId,
            ).toMap()

    companion object {
        const val TYPE = "import.apply"
        private const val MAX_NAME = 200
    }
}
