package com.proofu.worker.account

import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Full data export (docs/data/retention.md §전체 데이터 내보내기): every table the workspace
 * owns as `data.json`, the READY export files under `exports/`, and a README. Row order and
 * column names are the database's own so the archive documents itself.
 */
@Component
class AccountExportJobHandler(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) : JobHandler {
    private val log = LoggerFactory.getLogger(AccountExportJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val exportId = UUID.fromString(payload.get("exportId").asString())
        val w = job.workspaceId
        val status =
            jdbc
                .query(
                    "select status from account_exports where id = ? and workspace_id = ?",
                    { rs, _ -> rs.getString(1) },
                    exportId,
                    w,
                ).firstOrNull()
                ?: throw JobFailure("EXPORT_NOT_FOUND", "account export $exportId", retryable = false)
        if (status == "READY") return result(exportId, 0)

        val counts = linkedMapOf<String, Int>()
        val data = linkedMapOf<String, List<Map<String, Any?>>>()
        for ((table, sql) in TABLES) {
            val rows = jdbc.queryForList(sql, w).map { row -> row.mapValues { (_, v) -> plain(v) } }
            data[table] = rows
            counts[table] = rows.size
        }
        val files =
            jdbc.queryForList(
                """
                select e.id, e.format, d.title, f.content from exports e
                join export_files f on f.object_key = e.object_key
                join document_versions v on v.id = e.document_version_id
                join documents d on d.id = v.document_id
                where e.workspace_id = ? and e.status = 'READY'
                """.trimIndent(),
                w,
            )

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                """
                ProofU 전체 데이터 내보내기
                workspace: $w
                data.json: 테이블별 행 목록 (열 이름은 데이터베이스 그대로)
                exports/: 만든 문서 파일 (DOCX/PDF/MD/JSON)
                이 파일에는 개인정보가 들어 있습니다. 안전하게 보관하세요.
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data))
            zip.closeEntry()
            files.forEach { f ->
                val ext = (f["format"] as String).lowercase().let { if (it == "markdown") "md" else it }
                val title = (f["title"] as String).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(60)
                zip.putNextEntry(ZipEntry("exports/${f["id"]}-$title.$ext"))
                zip.write(f["content"] as ByteArray)
                zip.closeEntry()
            }
        }
        val bytes = out.toByteArray()
        if (bytes.size > MAX_BYTES) fail(exportId, "EXPORT_TOO_LARGE")
        val objectKey = "pg:account:$exportId"
        jdbc.update(
            "insert into export_files (object_key, content) values (?, ?) on conflict (object_key) do nothing",
            objectKey,
            bytes,
        )
        jdbc.update(
            """
            update account_exports set status = 'READY', object_key = ?, sha256 = ?, size_bytes = ?, tables = ?::jsonb,
                                       expires_at = now() + ?::interval, error_code = null
            where id = ?
            """.trimIndent(),
            objectKey,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            bytes.size.toLong(),
            mapper.writeValueAsString(counts),
            "${TTL.toDays()} days",
            exportId,
        )
        log.info("account.export workspace={} bytes={} tables={}", w, bytes.size, counts.size)
        return result(exportId, bytes.size)
    }

    private fun fail(
        id: UUID,
        code: String,
    ): Nothing {
        jdbc.update("update account_exports set status = 'FAILED', error_code = ? where id = ?", code, id)
        throw JobFailure(code, "account export $id: $code", retryable = false)
    }

    private fun result(
        id: UUID,
        bytes: Int,
    ) = mapper.writeValueAsString(mapOf("exportId" to id.toString(), "sizeBytes" to bytes))

    /** JSON-friendly values; timestamps and UUIDs become strings, jsonb stays a string of JSON. */
    private fun plain(v: Any?): Any? =
        when (v) {
            null -> null
            is Number, is Boolean, is String -> v
            is ByteArray -> "<${v.size} bytes>"
            else -> v.toString()
        }

    companion object {
        /** Must match apps/api JobTypes.ACCOUNT_EXPORT. */
        const val TYPE = "account.export"
        const val MAX_BYTES = 200 * 1024 * 1024
        val TTL: Duration = Duration.ofDays(7)

        private const val BY_WS = "where workspace_id = ? order by created_at"
        private const val BY_APP = "where application_id in (select id from applications where workspace_id = ?) order by created_at"
        private const val BY_CLAIM = "where claim_id in (select id from claims where workspace_id = ?)"
        private const val BY_DOC = "where document_id in (select id from documents where workspace_id = ?) order by created_at"

        val TABLES: List<Pair<String, String>> =
            listOf(
                "users" to
                    "select id, email, display_name, created_at from users where id = (select owner_user_id from workspaces where id = ?)",
                "user_profiles" to
                    "select * from user_profiles where user_id = (select owner_user_id from workspaces where id = ?)",
                "workspaces" to "select id, name, created_at from workspaces where id = ?",
                "workspace_settings" to "select * from workspace_settings where workspace_id = ?",
                "career_entries" to "select * from career_entries $BY_WS",
                "projects" to "select * from projects $BY_WS",
                "achievements" to "select * from achievements $BY_WS",
                "skills" to "select * from skills $BY_WS",
                "capabilities" to "select * from capabilities $BY_WS",
                "claims" to "select * from claims $BY_WS",
                "claim_sources" to "select * from claim_sources $BY_CLAIM",
                "evidence" to "select * from evidence $BY_WS",
                "claim_evidence" to "select * from claim_evidence $BY_CLAIM",
                "job_postings" to "select * from job_postings $BY_WS",
                "job_posting_snapshots" to
                    "select * from job_posting_snapshots where posting_id in (select id from job_postings where workspace_id = ?) order by created_at",
                "requirements" to
                    "select r.* from requirements r join job_posting_snapshots s on s.id = r.snapshot_id join job_postings p on p.id = s.posting_id where p.workspace_id = ? order by r.created_at",
                "applications" to "select * from applications $BY_WS",
                "application_status_events" to
                    "select * from application_status_events where application_id in (select id from applications where workspace_id = ?) order by occurred_at",
                "requirement_matches" to "select * from requirement_matches $BY_APP",
                "documents" to "select * from documents $BY_WS",
                "document_versions" to
                    "select id, document_id, parent_id, label, content_json, template_version, prompt_version, model_ref, created_by, created_at from document_versions $BY_DOC",
                "provenance_links" to
                    "select * from provenance_links where version_id in (select v.id from document_versions v join documents d on d.id = v.document_id where d.workspace_id = ?)",
                "submission_snapshots" to "select * from submission_snapshots $BY_APP",
                "reviews" to "select * from reviews $BY_APP",
                "exports" to
                    "select id, document_version_id, format, template_version, renderer_version, status, sha256, size_bytes, page_count, mime_type, created_at from exports $BY_WS",
                "ai_executions" to "select * from ai_executions $BY_WS",
                "audit_events" to "select * from audit_events where workspace_id = ? order by occurred_at",
            )
    }
}
