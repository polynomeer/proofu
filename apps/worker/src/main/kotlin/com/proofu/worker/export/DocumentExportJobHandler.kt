package com.proofu.worker.export

import com.proofu.domain.common.UnapprovedBlocksInExport
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.ExportGate
import com.proofu.domain.documents.ExportStatus
import com.proofu.renderer.ExportValidator
import com.proofu.renderer.RenderableDocument
import com.proofu.renderer.Renderers
import com.proofu.worker.documents.VersionContentReader
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
 * F07 (ADR-0009): REQUESTED → RENDERING → VALIDATING → READY | FAILED. The gate is checked
 * again here, a READY export of the same version/format/template is reused, and the file is
 * kept in export_files until an object store exists.
 */
@Component
class DocumentExportJobHandler(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
    private val content: VersionContentReader,
) : JobHandler {
    private val log = LoggerFactory.getLogger(DocumentExportJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val exportId = UUID.fromString(mapper.readTree(job.payload).get("exportId").asString())
        val export =
            jdbc
                .query(
                    """
                    select e.id, e.document_version_id, e.format, e.template_version, e.status,
                           d.title, d.type, d.language, a.company, a.role_title
                    from exports e
                    join document_versions v on v.id = e.document_version_id
                    join documents d on d.id = v.document_id
                    join applications a on a.id = d.application_id
                    where e.id = ? and e.workspace_id = ?
                    """.trimIndent(),
                    { rs, _ ->
                        ExportRow(
                            id = rs.getObject("id", UUID::class.java),
                            versionId = rs.getObject("document_version_id", UUID::class.java),
                            format = ExportFormat.valueOf(rs.getString("format")),
                            templateVersion = rs.getString("template_version"),
                            status = ExportStatus.valueOf(rs.getString("status")),
                            title = rs.getString("title"),
                            type = DocumentType.valueOf(rs.getString("type")),
                            language = rs.getString("language"),
                            company = rs.getString("company"),
                            roleTitle = rs.getString("role_title"),
                        )
                    },
                    exportId,
                    job.workspaceId,
                ).firstOrNull() ?: throw JobFailure("EXPORT_NOT_FOUND", "export $exportId", retryable = false)
        // Re-delivery after a crash: an export that already finished is left alone.
        if (export.status == ExportStatus.READY) return result(export.id, "READY", reused = false)
        if (export.status ==
            ExportStatus.FAILED
        ) {
            throw JobFailure("EXPORT_FAILED", "export already failed", retryable = false)
        }

        transition(export.id, ExportStatus.RENDERING)
        try {
            val reused = reuse(export)
            if (reused) return result(export.id, "READY", reused = true)

            val renderer = Renderers.forFormat(export.format) ?: fail(export.id, "FORMAT_NOT_IMPLEMENTED")
            val template =
                DocumentTemplate.find(export.templateVersion, export.type) ?: fail(export.id, "TEMPLATE_NOT_FOUND")
            val output = content.output(export.versionId)
            try {
                ExportGate.requireExportable(output)
            } catch (e: UnapprovedBlocksInExport) {
                fail(export.id, "UNSUPPORTED_CLAIM_IN_EXPORT", e)
            }
            val document =
                RenderableDocument.of(
                    title = export.title,
                    type = export.type,
                    language = export.language,
                    company = export.company,
                    roleTitle = export.roleTitle,
                    template = template,
                    output = output,
                )
            val rendered = renderer.render(document)
            if (rendered.bytes.size > MAX_BYTES) fail(export.id, "EXPORT_TOO_LARGE")

            transition(export.id, ExportStatus.VALIDATING)
            val problems = ExportValidator.validate(rendered, document)
            if (problems.isNotEmpty()) {
                log.warn("document.export export={} validation failed: {}", export.id, problems.size)
                fail(export.id, "RENDER_VALIDATION_FAILED")
            }

            val objectKey = "pg:${export.id}"
            val sha256 = sha256(rendered.bytes)
            tx.execute {
                jdbc.update(
                    "insert into export_files (object_key, content) values (?, ?) on conflict (object_key) do nothing",
                    objectKey,
                    rendered.bytes,
                )
                jdbc.update(
                    """
                    update exports set status = 'READY', renderer_version = ?, object_key = ?, sha256 = ?, size_bytes = ?,
                                       page_count = ?, mime_type = ?, error_code = null
                    where id = ?
                    """.trimIndent(),
                    rendered.rendererVersion,
                    objectKey,
                    sha256,
                    rendered.bytes.size.toLong(),
                    rendered.pageCount,
                    export.format.mimeType,
                    export.id,
                )
            }
            log.info(
                "document.export export={} format={} bytes={} renderer={}",
                export.id,
                export.format,
                rendered.bytes.size,
                rendered.rendererVersion,
            )
            return result(export.id, "READY", reused = false)
        } catch (e: JobFailure) {
            throw e
        } catch (e: RuntimeException) {
            fail(export.id, "RENDER_FAILED", e)
        }
    }

    /** ADR-0009 §5: a READY export of the same version, format and template points at the same file. */
    private fun reuse(export: ExportRow): Boolean {
        val ready =
            jdbc
                .query(
                    """
                    select object_key, sha256, size_bytes, page_count, mime_type, renderer_version from exports
                    where document_version_id = ? and format = ? and template_version = ? and status = 'READY' and id <> ?
                    order by created_at desc limit 1
                    """.trimIndent(),
                    { rs, _ ->
                        listOf(
                            rs.getString("object_key"),
                            rs.getString("sha256"),
                            rs.getLong("size_bytes"),
                            rs.getInt("page_count").takeUnless { rs.wasNull() },
                            rs.getString("mime_type"),
                            rs.getString("renderer_version"),
                        )
                    },
                    export.versionId,
                    export.format.name,
                    export.templateVersion,
                    export.id,
                ).firstOrNull() ?: return false
        jdbc.update(
            """
            update exports set status = 'READY', object_key = ?, sha256 = ?, size_bytes = ?, page_count = ?, mime_type = ?,
                               renderer_version = ?, error_code = null
            where id = ?
            """.trimIndent(),
            ready[0],
            ready[1],
            ready[2],
            ready[3],
            ready[4],
            ready[5],
            export.id,
        )
        return true
    }

    private fun transition(
        id: UUID,
        to: ExportStatus,
    ) {
        val current =
            ExportStatus.valueOf(
                jdbc.queryForObject("select status from exports where id = ?", String::class.java, id)!!,
            )
        jdbc.update("update exports set status = ? where id = ?", current.transitionTo(to).name, id)
    }

    private fun fail(
        id: UUID,
        code: String,
        cause: Throwable? = null,
    ): Nothing {
        jdbc.update("update exports set status = 'FAILED', error_code = ? where id = ?", code, id)
        throw JobFailure(code, "export $id: $code", retryable = false, cause = cause)
    }

    private fun result(
        id: UUID,
        status: String,
        reused: Boolean,
    ) = mapper.writeValueAsString(mapOf("exportId" to id.toString(), "status" to status, "reused" to reused))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class ExportRow(
        val id: UUID,
        val versionId: UUID,
        val format: ExportFormat,
        val templateVersion: String,
        val status: ExportStatus,
        val title: String,
        val type: DocumentType,
        val language: String,
        val company: String,
        val roleTitle: String,
    )

    companion object {
        /** Must match apps/api JobTypes.DOCUMENT_EXPORT. */
        const val TYPE = "document.export"
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
