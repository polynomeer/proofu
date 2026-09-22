package com.proofu.worker.account

import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Removes everything a workspace owns (docs/data/retention.md §계정 삭제) in one transaction.
 * `SET LOCAL proofu.purge = 'on'` is what lets the immutable-table triggers accept DELETE here
 * and nowhere else. The user and workspace rows stay as tombstones without personal data.
 * Idempotent: a re-delivered job finds nothing left and still succeeds.
 */
@Component
class AccountPurgeJobHandler(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
) : JobHandler {
    private val log = LoggerFactory.getLogger(AccountPurgeJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val userId = UUID.fromString(payload.get("userId").asString())
        val workspaceId = UUID.fromString(payload.get("workspaceId").asString())
        if (workspaceId !=
            job.workspaceId
        ) {
            throw JobFailure("PURGE_WORKSPACE_MISMATCH", "payload names another workspace", retryable = false)
        }
        val marked =
            jdbc.queryForObject(
                "select count(*) from workspaces where id = ? and owner_user_id = ? and deleted_at is not null",
                Long::class.java,
                workspaceId,
                userId,
            ) ?: 0L
        if (marked == 0L) throw JobFailure("PURGE_NOT_REQUESTED", "workspace is not marked deleted", retryable = false)

        val counts = linkedMapOf<String, Int>()
        tx.execute {
            jdbc.execute("set local proofu.purge = 'on'")

            fun run(
                label: String,
                sql: String,
                vararg args: Any,
            ) {
                counts[label] = (counts[label] ?: 0) + jdbc.update(sql, *args)
            }
            val w = workspaceId
            // Derived artefacts first, then the source-of-truth, so every FK is satisfied on the way down.
            run(
                "export_files",
                "delete from export_files where object_key in (select object_key from exports where workspace_id = ?)",
                w,
            )
            run("exports", "delete from exports where workspace_id = ?", w)
            run(
                "provenance_links",
                "delete from provenance_links where version_id in (select v.id from document_versions v join documents d on d.id = v.document_id where d.workspace_id = ?)",
                w,
            )
            run(
                "submission_snapshots",
                "delete from submission_snapshots where application_id in (select id from applications where workspace_id = ?)",
                w,
            )
            run(
                "document_versions",
                "delete from document_versions where document_id in (select id from documents where workspace_id = ?)",
                w,
            )
            run("documents", "delete from documents where workspace_id = ?", w)
            run(
                "reviews",
                "delete from reviews where application_id in (select id from applications where workspace_id = ?)",
                w,
            )
            run(
                "interview_handoffs",
                "delete from interview_handoffs where application_id in (select id from applications where workspace_id = ?)",
                w,
            )
            run(
                "requirement_matches",
                "delete from requirement_matches where application_id in (select id from applications where workspace_id = ?)",
                w,
            )
            run(
                "application_status_events",
                "delete from application_status_events where application_id in (select id from applications where workspace_id = ?)",
                w,
            )
            run("applications", "delete from applications where workspace_id = ?", w)
            run(
                "requirements",
                "delete from requirements where snapshot_id in (select s.id from job_posting_snapshots s join job_postings p on p.id = s.posting_id where p.workspace_id = ?)",
                w,
            )
            run(
                "job_posting_snapshots",
                "delete from job_posting_snapshots where posting_id in (select id from job_postings where workspace_id = ?)",
                w,
            )
            run("job_postings", "delete from job_postings where workspace_id = ?", w)
            run(
                "claim_evidence",
                "delete from claim_evidence where claim_id in (select id from claims where workspace_id = ?)",
                w,
            )
            run(
                "claim_sources",
                "delete from claim_sources where claim_id in (select id from claims where workspace_id = ?)",
                w,
            )
            run(
                "capability_evidence",
                "delete from capability_evidence where capability_id in (select id from capabilities where workspace_id = ?)",
                w,
            )
            run("claims", "delete from claims where workspace_id = ?", w)
            run("evidence", "delete from evidence where workspace_id = ?", w)
            run("achievements", "delete from achievements where workspace_id = ?", w)
            run(
                "project_skills",
                "delete from project_skills where project_id in (select id from projects where workspace_id = ?)",
                w,
            )
            run("projects", "delete from projects where workspace_id = ?", w)
            run("skills", "delete from skills where workspace_id = ?", w)
            run("capabilities", "delete from capabilities where workspace_id = ?", w)
            run("career_entries", "delete from career_entries where workspace_id = ?", w)
            run("ai_executions", "delete from ai_executions where workspace_id = ?", w)
            run("jobs", "delete from jobs where workspace_id = ? and id <> ?", w, job.id)
            run("user_profiles", "delete from user_profiles where user_id = ?", userId)
            run("workspace_members", "delete from workspace_members where workspace_id = ?", w)
            // Tombstones: keep the ids for audit rows, drop every personal detail and the login identity.
            run(
                "users",
                """
                update users set email = ?, display_name = '삭제된 사용자', oidc_subject = ?, deleted_at = coalesce(deleted_at, now())
                where id = ?
                """.trimIndent(),
                "deleted:$userId@invalid",
                "deleted:$userId",
                userId,
            )
            run(
                "workspaces",
                "update workspaces set name = 'deleted', deleted_at = coalesce(deleted_at, now()) where id = ?",
                w,
            )
        }
        log.info("account.purge workspace={} rows={}", workspaceId, counts.values.sum())
        return mapper.writeValueAsString(
            mapOf("workspaceId" to workspaceId.toString(), "deleted" to counts, "providerUserDeleted" to false),
        )
    }

    companion object {
        /** Must match apps/api JobTypes.ACCOUNT_PURGE. */
        const val TYPE = "account.purge"
    }
}
