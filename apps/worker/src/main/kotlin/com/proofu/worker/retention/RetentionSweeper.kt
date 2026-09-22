package com.proofu.worker.retention

import com.proofu.domain.identity.RetentionPolicy
import com.proofu.worker.jobs.WorkspaceSettingsReader
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Applies each workspace's [RetentionPolicy] (docs/data/retention.md §보존 기간 설정): purges
 * source records that sat in the trash past the window and expires export files. A system
 * task, not a job: nothing a user asked for, so it runs on a timer and reports per workspace.
 *
 * Only source data is purged, and only when nothing alive still points at it. Postings,
 * applications and documents own immutable children and stay until the account is deleted.
 */
@Component
class RetentionSweeper(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val settings: WorkspaceSettingsReader,
    private val mapper: ObjectMapper,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(RetentionSweeper::class.java)

    @Scheduled(
        fixedDelayString = "\${proofu.worker.retention-interval:1h}",
        initialDelayString = "\${proofu.worker.retention-initial-delay:1m}",
    )
    fun sweepAll() {
        val workspaces =
            jdbc.query(
                "select id from workspaces where deleted_at is null",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
            )
        workspaces.forEach { w ->
            try {
                sweep(w)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                log.error("retention sweep failed for workspace {}", w, e)
            }
        }
    }

    /** One workspace; returns the count of rows removed per table (only non-zero entries). */
    fun sweep(workspaceId: UUID): Map<String, Int> {
        val policy = settings.retentionFor(workspaceId)
        val now = Instant.now(clock)
        val counts = linkedMapOf<String, Int>()
        tx.execute {
            val w = workspaceId
            val trash = OffsetDateTime.ofInstant(policy.trashCutoff(now), clock.zone)

            fun run(
                label: String,
                sql: String,
                vararg args: Any,
            ) {
                val n = jdbc.update(sql, *args)
                if (n > 0) counts[label] = (counts[label] ?: 0) + n
            }

            // --- trash: children before parents, and never a row something alive still cites ---
            run(
                "achievements",
                """
                delete from achievements a where a.workspace_id = ? and a.deleted_at < ?
                  and not exists (select 1 from claim_sources s join claims c on c.id = s.claim_id
                                  where s.source_type = 'ACHIEVEMENT' and s.source_id = a.id and c.deleted_at is null)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "project_skills",
                """
                delete from project_skills ps using projects p where p.id = ps.project_id and p.workspace_id = ? and p.deleted_at < ?
                  and not exists (select 1 from achievements a where a.project_id = p.id)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "projects",
                """
                delete from projects p where p.workspace_id = ? and p.deleted_at < ?
                  and not exists (select 1 from achievements a where a.project_id = p.id)
                  and not exists (select 1 from claim_sources s join claims c on c.id = s.claim_id
                                  where s.source_type = 'PROJECT' and s.source_id = p.id and c.deleted_at is null)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "career_entries",
                """
                delete from career_entries e where e.workspace_id = ? and e.deleted_at < ?
                  and not exists (select 1 from projects p where p.career_entry_id = e.id)
                  and not exists (select 1 from claim_sources s join claims c on c.id = s.claim_id
                                  where s.source_type = 'CAREER_ENTRY' and s.source_id = e.id and c.deleted_at is null)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "skills",
                """
                delete from skills k where k.workspace_id = ? and k.deleted_at < ?
                  and not exists (select 1 from project_skills ps where ps.skill_id = k.id)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "capability_evidence",
                """
                delete from capability_evidence ce using capabilities c where c.id = ce.capability_id
                  and c.workspace_id = ? and c.deleted_at < ?
                  and not exists (select 1 from capabilities child where child.parent_id = c.id)
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "capabilities",
                """
                delete from capabilities c where c.workspace_id = ? and c.deleted_at < ?
                  and not exists (select 1 from capabilities child where child.parent_id = c.id)
                """.trimIndent(),
                w,
                trash,
            )
            // Claims: their own links go with them; match rows for a permanently deleted claim are
            // meaningless (the report already hides deleted claims) and would otherwise block the FK.
            run(
                "claim_requirement_matches",
                "delete from requirement_matches m using claims c where c.id = m.claim_id and c.workspace_id = ? and c.deleted_at < ?",
                w,
                trash,
            )
            run(
                "claim_sources",
                "delete from claim_sources s using claims c where c.id = s.claim_id and c.workspace_id = ? and c.deleted_at < ?",
                w,
                trash,
            )
            run(
                "claim_evidence",
                "delete from claim_evidence l using claims c where c.id = l.claim_id and c.workspace_id = ? and c.deleted_at < ?",
                w,
                trash,
            )
            run("claims", "delete from claims where workspace_id = ? and deleted_at < ?", w, trash)
            run(
                "claim_evidence",
                "delete from claim_evidence l using evidence e where e.id = l.evidence_id and e.workspace_id = ? and e.deleted_at < ?",
                w,
                trash,
            )
            run(
                "capability_evidence",
                "delete from capability_evidence l using evidence e where e.id = l.evidence_id and e.workspace_id = ? and e.deleted_at < ?",
                w,
                trash,
            )
            run("evidence", "delete from evidence where workspace_id = ? and deleted_at < ?", w, trash)
            run(
                "requirement_matches",
                """
                delete from requirement_matches m using requirements r, job_posting_snapshots s, job_postings p
                where r.id = m.requirement_id and s.id = r.snapshot_id and p.id = s.posting_id
                  and p.workspace_id = ? and r.deleted_at < ?
                """.trimIndent(),
                w,
                trash,
            )
            run(
                "requirements",
                """
                delete from requirements r using job_posting_snapshots s, job_postings p
                where s.id = r.snapshot_id and p.id = s.posting_id and p.workspace_id = ? and r.deleted_at < ?
                """.trimIndent(),
                w,
                trash,
            )

            // --- exports: the row becomes EXPIRED, the bytes go once no READY row shares them ---
            val exportCutoff = OffsetDateTime.ofInstant(policy.exportCutoff(now), clock.zone)
            val expiredKeys =
                jdbc.query(
                    """
                    update exports set status = 'EXPIRED', expires_at = now()
                    where workspace_id = ? and status = 'READY' and created_at < ? returning object_key
                    """.trimIndent(),
                    { rs, _ -> rs.getString("object_key") },
                    w,
                    exportCutoff,
                )
            if (expiredKeys.isNotEmpty()) counts["exports"] = expiredKeys.size
            val expiredAccountKeys =
                jdbc.query(
                    """
                    update account_exports set status = 'EXPIRED'
                    where workspace_id = ? and status = 'READY' and expires_at < ? returning object_key
                    """.trimIndent(),
                    { rs, _ -> rs.getString("object_key") },
                    w,
                    OffsetDateTime.ofInstant(now, clock.zone),
                )
            if (expiredAccountKeys.isNotEmpty()) counts["account_exports"] = expiredAccountKeys.size
            (expiredKeys + expiredAccountKeys).filterNotNull().distinct().forEach { key ->
                run(
                    "export_files",
                    """
                    delete from export_files f where f.object_key = ?
                      and not exists (select 1 from exports e where e.object_key = f.object_key and e.status = 'READY')
                      and not exists (select 1 from account_exports e where e.object_key = f.object_key and e.status = 'READY')
                    """.trimIndent(),
                    key,
                )
            }

            if (counts.isNotEmpty()) {
                val summary = mapper.writeValueAsString(counts)
                jdbc.update(
                    """
                    insert into audit_events (id, workspace_id, actor_type, action, target_type, target_id, after_hash)
                    values (?, ?, 'SYSTEM', 'retention.swept', 'workspace', ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    w,
                    w,
                    sha256(summary),
                )
                log.info(
                    "retention.swept workspace={} trashDays={} exportDays={} {}",
                    w,
                    policy.trashDays,
                    policy.exportDays,
                    summary,
                )
            }
        }
        return counts
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
