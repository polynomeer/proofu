package com.proofu.api.export

import com.proofu.api.audit.AuditLog
import com.proofu.api.document.DocumentBlockDto
import com.proofu.api.document.DocumentRepository
import com.proofu.api.document.DocumentVersionStore
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ExportNotReadyException
import com.proofu.api.web.NotImplementedException
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.ExportGate
import com.proofu.domain.documents.ExportStatus
import com.proofu.domain.documents.GeneratedOutput
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

/** Accepts export requests (ADR-0009) and serves their files; rendering happens in the worker. */
@Service
class ExportService(
    private val versions: DocumentVersionStore,
    private val documents: DocumentRepository,
    private val jobs: JobService,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val audit: AuditLog,
) {
    @Transactional
    fun start(
        workspace: WorkspaceContext,
        versionId: UUID,
        format: ExportFormat,
    ): ExportAccepted {
        val version =
            versions.find(versionId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document version", versionId)
        if (!format.implemented) throw NotImplementedException("${format.name} export")
        ExportGate.requireExportable(GeneratedOutput(version.blocks.map(DocumentBlockDto::toDomain)))

        val dedupeKey = "$versionId:${format.name}"
        val exportId = ids.next()
        val jobId =
            jobs.enqueue(
                workspace,
                JobTypes.DOCUMENT_EXPORT,
                mapOf(
                    "exportId" to exportId.toString(),
                    "documentVersionId" to versionId.toString(),
                    "format" to format.name,
                ),
                dedupeKey = dedupeKey,
            )
        // A pending job for the same version and format already has its export row.
        val existing =
            jdbc
                .query(
                    "select id from exports where job_id = ?",
                    { rs, _ -> rs.getObject("id", UUID::class.java) },
                    jobId,
                ).firstOrNull()
        if (existing != null) return ExportAccepted(jobId, existing)

        jdbc.update(
            """
            insert into exports (id, workspace_id, document_version_id, format, template_version, status, job_id)
            values (?, ?, ?, ?, ?, 'REQUESTED', ?)
            """.trimIndent(),
            exportId,
            workspace.workspaceId.value,
            versionId,
            format.name,
            version.templateVersion,
            jobId,
        )
        audit.record(
            workspace,
            "export.requested",
            TARGET,
            exportId,
            after =
                mapOf(
                    "format" to format.name,
                    "jobId" to jobId,
                ),
        )
        return ExportAccepted(jobId, exportId)
    }

    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        versionId: UUID,
    ): ExportList {
        versions.find(versionId, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException("document version", versionId)
        return ExportList(
            jdbc.query(
                "$SELECT where e.document_version_id = ? and e.workspace_id = ? order by e.created_at desc, e.id desc",
                { rs, _ -> row(rs) },
                versionId,
                workspace.workspaceId.value,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ExportResponse = find(workspace, id)

    @Transactional(readOnly = true)
    fun file(
        workspace: WorkspaceContext,
        id: UUID,
    ): ExportFile {
        val export = find(workspace, id)
        if (export.status != ExportStatus.READY) throw ExportNotReadyException(id, export.status.name)
        val objectKey =
            jdbc.queryForObject("select object_key from exports where id = ?", String::class.java, id)
                ?: throw ExportNotReadyException(id, "READY without a file")
        val bytes =
            jdbc
                .query(
                    "select content from export_files where object_key = ?",
                    { rs, _ -> rs.getBytes("content") },
                    objectKey,
                ).firstOrNull() ?: throw ResourceNotFoundException("export file", id)
        return ExportFile(export.fileName, export.mimeType ?: export.format.mimeType, bytes)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ExportResponse =
        jdbc
            .query(
                "$SELECT where e.id = ? and e.workspace_id = ?",
                { rs, _ -> row(rs) },
                id,
                workspace.workspaceId.value,
            ).firstOrNull() ?: throw ResourceNotFoundException(TARGET, id)

    private fun row(rs: ResultSet): ExportResponse {
        val format = ExportFormat.valueOf(rs.getString("format"))
        return ExportResponse(
            id = rs.getObject("id", UUID::class.java),
            documentVersionId = rs.getObject("document_version_id", UUID::class.java),
            format = format,
            templateVersion = rs.getString("template_version"),
            rendererVersion = rs.getString("renderer_version"),
            status = ExportStatus.valueOf(rs.getString("status")),
            errorCode = rs.getString("error_code"),
            sha256 = rs.getString("sha256"),
            sizeBytes = rs.getLong("size_bytes").takeUnless { rs.wasNull() },
            pageCount = rs.getInt("page_count").takeUnless { rs.wasNull() },
            mimeType = rs.getString("mime_type"),
            fileName = fileName(rs.getString("document_title"), format),
            jobId = rs.getObject("job_id", UUID::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
        )
    }

    companion object {
        private const val TARGET = "export"
        private val SELECT =
            """
            select e.*, d.title as document_title
            from exports e
            join document_versions v on v.id = e.document_version_id
            join documents d on d.id = v.document_id
            """.trimIndent()

        /** Title with filesystem-unsafe characters removed; the header carries it as RFC 5987 UTF-8. */
        fun fileName(
            title: String,
            format: ExportFormat,
        ): String {
            val safe =
                title
                    .replace(
                        Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),
                        " ",
                    ).replace(Regex("\\s+"), " ")
                    .trim()
                    .ifEmpty {
                        "document"
                    }
            return "${safe.take(80)}.${format.extension}"
        }
    }
}
