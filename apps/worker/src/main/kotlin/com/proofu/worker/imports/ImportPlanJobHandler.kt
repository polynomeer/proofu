package com.proofu.worker.imports

import com.proofu.domain.imports.ImportPlanner
import com.proofu.domain.imports.ImportedBefore
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID

/**
 * F21 1단계: works out what a bundle would do and writes only the ledger
 * (docs/product/personal-archive-import.md §흐름).
 *
 * The bundle lives in the job payload and goes when the job does — nothing of it is kept, the way
 * F13 keeps no resume text. What survives is `import_runs`/`import_items`, which is the answer to
 * "did I already bring this in, and has it moved since".
 *
 * The content hash is computed here rather than taken from the sender: an adapter that hashes
 * differently, or forgets to, would break idempotency without anyone noticing.
 */
@Component
class ImportPlanJobHandler(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
    private val hashes: ImportHashing,
) : JobHandler {
    private val log = LoggerFactory.getLogger(ImportPlanJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val items = hashes.itemsOf(payload)
        if (items.isEmpty()) throw JobFailure("VALIDATION_FAILED", "bundle has no items", retryable = false)

        val imported = importedBefore(job.workspaceId, items.map { it.sourceKey })
        val plan = ImportPlanner.plan(items, imported)
        val runId = UUID.randomUUID()
        val bundleHash = sha256(items.joinToString("\n") { "${it.sourceKey}:${it.contentHash}" })

        tx.execute {
            jdbc.update(
                """
                insert into import_runs (id, workspace_id, bundle_hash, source, status, item_count)
                values (?, ?, ?, ?, 'PLANNED', ?)
                """.trimIndent(),
                runId,
                job.workspaceId,
                bundleHash,
                payload.get("source")?.asString()?.take(MAX_SOURCE),
                plan.size,
            )
            plan.forEach { entry ->
                jdbc.update(
                    """
                    insert into import_items
                      (id, run_id, workspace_id, source_key, source_path, type, content_hash, status, reason)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runId,
                    job.workspaceId,
                    entry.item.sourceKey,
                    entry.item.sourcePath,
                    entry.item.type.name,
                    entry.item.contentHash,
                    entry.status.name,
                    entry.reason,
                )
            }
        }

        val summary = ImportPlanner.summary(plan).mapKeys { it.key.name }
        // Counts only: a source key or a field value can carry someone's name (privacy §12.2).
        log.info("import.plan run={} items={} {}", runId, plan.size, summary)
        return mapper.writeValueAsString(mapOf("runId" to runId.toString(), "summary" to summary))
    }

    /** The newest applied verdict per key, and whether the row it made was touched here since. */
    private fun importedBefore(
        workspaceId: UUID,
        keys: List<String>,
    ): Map<String, ImportedBefore> {
        if (keys.isEmpty()) return emptyMap()
        val placeholders = keys.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select distinct on (source_key) source_key, content_hash, target_type, target_id, applied_at
                from import_items
                where workspace_id = ? and applied_at is not null and source_key in ($placeholders)
                order by source_key, applied_at desc
                """.trimIndent(),
                { rs, _ ->
                    val key = rs.getString("source_key")
                    val hash = rs.getString("content_hash")
                    key to
                        ImportedBefore(
                            hash,
                            editedSince =
                                editedSince(
                                    rs.getString("target_type"),
                                    rs.getObject("target_id", UUID::class.java),
                                    rs.getObject("applied_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                                ),
                        )
                },
                workspaceId,
                *keys.toTypedArray(),
            ).toMap()
    }

    /**
     * Whether the row this key produced was changed in ProofU after the import put it there. Only
     * applications carry a version we can compare; a posting snapshot is immutable, so it cannot
     * have been edited at all.
     */
    private fun editedSince(
        targetType: String?,
        targetId: UUID?,
        appliedAt: java.time.Instant?,
    ): Boolean {
        if (targetType != "application" || targetId == null || appliedAt == null) return false
        val updated =
            jdbc
                .query(
                    "select updated_at from applications where id = ?",
                    { rs, _ -> rs.getObject("updated_at", java.time.OffsetDateTime::class.java)?.toInstant() },
                    targetId,
                ).firstOrNull() ?: return false
        return updated.isAfter(appliedAt)
    }

    /** Identifies the bundle as a whole, so the same file is recognised when it is sent twice. */
    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val TYPE = "import.plan"
        private const val MAX_SOURCE = 120
    }
}
