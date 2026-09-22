package com.proofu.api.account

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.AuthProperties
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ExportNotReadyException
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.common.IdGenerator
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Account deletion (docs/data/retention.md): the caller proves a recent login, the user and
 * workspace are marked deleted so the next request is refused, and the purge job does the rest.
 */
@Service
class AccountService(
    private val jdbc: JdbcTemplate,
    private val jobs: JobService,
    private val audit: AuditLog,
    private val auth: AuthProperties,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val mapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun listExports(workspace: WorkspaceContext): AccountExportList =
        AccountExportList(
            jdbc.query(
                "select * from account_exports where workspace_id = ? order by created_at desc, id desc",
                { rs, _ -> exportRow(rs) },
                workspace.workspaceId.value,
            ),
        )

    /** Full export: one job per request; a pending request for the workspace is reused. */
    @Transactional
    fun requestExport(workspace: WorkspaceContext): UUID {
        val workspaceId = workspace.workspaceId.value
        val exportId = ids.next()
        val jobId =
            jobs.enqueue(
                workspace,
                JobTypes.ACCOUNT_EXPORT,
                mapOf("exportId" to exportId.toString(), "workspaceId" to workspaceId.toString()),
                dedupeKey = "export:$workspaceId",
            )
        val existing =
            jdbc
                .query(
                    "select id from account_exports where job_id = ?",
                    { rs, _ -> rs.getObject("id", UUID::class.java) },
                    jobId,
                ).firstOrNull()
        if (existing != null) return jobId
        jdbc.update(
            "insert into account_exports (id, workspace_id, job_id, status) values (?, ?, ?, 'REQUESTED')",
            exportId,
            workspaceId,
            jobId,
        )
        audit.record(
            workspace,
            "account.export_requested",
            "workspace",
            workspaceId,
            after =
                mapOf(
                    "exportId" to exportId,
                ),
        )
        return jobId
    }

    /** The archive itself: everything the user has, so a recent login is required (threat-model). */
    @Transactional
    fun exportFile(
        workspace: WorkspaceContext,
        id: UUID,
    ): AccountExportFile {
        workspace.requireRecentAuthentication(clock, auth.reauthMaxAge)
        val row =
            jdbc
                .query("select * from account_exports where id = ? and workspace_id = ?", { rs, _ ->
                    exportRow(rs) to
                        rs.getString("object_key")
                }, id, workspace.workspaceId.value)
                .firstOrNull() ?: throw ResourceNotFoundException("account export", id)
        val (export, objectKey) = row
        val expired = export.expiresAt != null && export.expiresAt.isBefore(Instant.now(clock))
        if (export.status != "READY" ||
            expired ||
            objectKey == null
        ) {
            throw ExportNotReadyException(id, if (expired) "EXPIRED" else export.status)
        }
        val bytes =
            jdbc
                .query(
                    "select content from export_files where object_key = ?",
                    { rs, _ -> rs.getBytes("content") },
                    objectKey,
                ).firstOrNull()
                ?: throw ResourceNotFoundException("account export file", id)
        audit.record(
            workspace,
            "account.export_downloaded",
            "workspace",
            workspace.workspaceId.value,
            after =
                mapOf(
                    "exportId" to id,
                ),
        )
        return AccountExportFile("proofu-data-${export.createdAt.toString().take(10)}.zip", bytes)
    }

    private fun exportRow(rs: java.sql.ResultSet): AccountExportResponse =
        AccountExportResponse(
            id = rs.getObject("id", UUID::class.java),
            status = rs.getString("status"),
            errorCode = rs.getString("error_code"),
            sizeBytes = rs.getLong("size_bytes").takeUnless { rs.wasNull() },
            sha256 = rs.getString("sha256"),
            tables =
                rs.getString("tables")?.let { mapper.readValue(it, Map::class.java) }?.let { m ->
                    m.entries.associate {
                        it.key.toString() to
                            (it.value as Number).toInt()
                    }
                },
            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java)?.toInstant(),
            jobId = rs.getObject("job_id", UUID::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        )

    @Transactional
    fun requestDeletion(workspace: WorkspaceContext): UUID {
        workspace.requireRecentAuthentication(clock, auth.reauthMaxAge)
        val userId = workspace.userId.value
        val workspaceId = workspace.workspaceId.value
        jdbc.update("update users set deleted_at = now() where id = ? and deleted_at is null", userId)
        jdbc.update("update workspaces set deleted_at = now() where id = ? and deleted_at is null", workspaceId)
        val jobId =
            jobs.enqueue(
                workspace,
                JobTypes.ACCOUNT_PURGE,
                mapOf("userId" to userId.toString(), "workspaceId" to workspaceId.toString()),
                dedupeKey = workspaceId.toString(),
            )
        audit.record(workspace, "account.deletion_requested", "workspace", workspaceId, after = mapOf("jobId" to jobId))
        return jobId
    }
}

data class AccountExportResponse(
    val id: UUID,
    val status: String,
    val errorCode: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val tables: Map<String, Int>?,
    val expiresAt: Instant?,
    val jobId: UUID?,
    val createdAt: Instant,
)

data class AccountExportList(
    val items: List<AccountExportResponse>,
)

data class AccountExportFile(
    val fileName: String,
    val bytes: ByteArray,
)
