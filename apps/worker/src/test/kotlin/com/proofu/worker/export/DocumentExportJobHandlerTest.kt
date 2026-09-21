package com.proofu.worker.export

import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.jobs.ContentHash
import com.proofu.renderer.ExportValidator
import com.proofu.renderer.Rendered
import com.proofu.worker.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class DocumentExportJobHandlerTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `renders an approved version to DOCX, validates it, stores the file and reuses it for the next request`() {
        val s = seed()
        val first = requestExport(s, "DOCX")
        awaitStatus(first.job, "SUCCEEDED")

        val export = jdbc.queryForMap("select * from exports where id = ?", first.export)
        assertThat(export["status"]).isEqualTo("READY")
        assertThat(export["renderer_version"]).isEqualTo("docx-poi-1")
        assertThat(export["object_key"]).isEqualTo("pg:${first.export}")
        assertThat(export["mime_type"].toString()).contains("wordprocessingml")
        assertThat((export["sha256"] as String)).hasSize(64)
        val bytes =
            jdbc.queryForObject(
                "select content from export_files where object_key = ?",
                ByteArray::class.java,
                "pg:${first.export}",
            )!!
        assertThat(bytes.size.toLong()).isEqualTo(export["size_bytes"])
        val text = ExportValidator.extractText(Rendered(bytes, ExportFormat.DOCX, "docx-poi-1", null))
        assertThat(
            text,
        ).contains("이력서").contains("요약 문장").contains("경력 문장").doesNotContain("summary-1").doesNotContain("UNSUPPORTED")

        // Same version/format/template again: no new file, same object key.
        val second = requestExport(s, "DOCX")
        awaitStatus(second.job, "SUCCEEDED")
        val reused = jdbc.queryForMap("select status, object_key, sha256 from exports where id = ?", second.export)
        assertThat(reused["status"]).isEqualTo("READY")
        assertThat(reused["object_key"]).isEqualTo("pg:${first.export}")
        assertThat(reused["sha256"]).isEqualTo(export["sha256"])
        assertThat(
            jdbc.queryForObject(
                "select count(*) from export_files where object_key like ?",
                Long::class.java,
                "pg:${first.export}%",
            ),
        ).isEqualTo(1L)
        assertThat(
            jdbc.queryForObject("select result ->> 'reused' from jobs where id = ?", String::class.java, second.job),
        ).isEqualTo("true")

        // PDF: embedded Korean font, page count recorded.
        val pdf = requestExport(s, "PDF")
        awaitStatus(pdf.job, "SUCCEEDED")
        val pdfRow =
            jdbc.queryForMap(
                "select status, page_count, mime_type, renderer_version from exports where id = ?",
                pdf.export,
            )
        assertThat(pdfRow["status"]).isEqualTo("READY")
        assertThat(pdfRow["page_count"]).isEqualTo(1)
        assertThat(pdfRow["mime_type"]).isEqualTo("application/pdf")
        assertThat(pdfRow["renderer_version"]).isEqualTo("pdf-openpdf-1")

        // An unapproved version fails at the gate and the export records why.
        val pending = version(s.documentId, approved = false)
        val third = requestExport(s, "MARKDOWN", pending)
        awaitStatus(third.job, "FAILED:UNSUPPORTED_CLAIM_IN_EXPORT")
        assertThat(jdbc.queryForMap("select status, error_code from exports where id = ?", third.export))
            .containsEntry("status", "FAILED")
            .containsEntry("error_code", "UNSUPPORTED_CLAIM_IN_EXPORT")
    }

    private data class Seed(
        val workspaceId: UUID,
        val documentId: UUID,
        val versionId: UUID,
    )

    private data class Requested(
        val job: UUID,
        val export: UUID,
    )

    private fun seed(): Seed {
        val userId = UUID.randomUUID()
        val ws = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", ws, userId)
        val postingId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            postingId,
            ws,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', 't', ?, now())",
            snapshotId,
            postingId,
            ContentHash.of("t"),
        )
        val applicationId = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title) values (?, ?, ?, 'ABC', 'PM')",
            applicationId,
            ws,
            snapshotId,
        )
        val documentId = UUID.randomUUID()
        jdbc.update(
            "insert into documents (id, workspace_id, application_id, type, title) values (?, ?, ?, 'RESUME', '이력서')",
            documentId,
            ws,
            applicationId,
        )
        return Seed(ws, documentId, version(documentId, approved = true))
    }

    private fun version(
        documentId: UUID,
        approved: Boolean,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) values (?, ?, ?::jsonb, 'ko-v1', 'USER')",
            id,
            documentId,
            """{"blocks":[
              {"blockId":"summary-1","text":"요약 문장","claimRefs":[],"evidenceRefs":[],"requirementRefs":[],"certainty":"UNSUPPORTED","warnings":[],"approvedByUser":$approved},
              {"blockId":"career-1","text":"경력 문장","claimRefs":["${UUID.randomUUID()}"],"evidenceRefs":[],"requirementRefs":[],"certainty":"SUPPORTED","warnings":[],"approvedByUser":false}
            ]}""",
        )
        return id
    }

    private fun requestExport(
        s: Seed,
        format: String,
        versionId: UUID = s.versionId,
    ): Requested {
        val job = UUID.randomUUID()
        val export = UUID.randomUUID()
        jdbc.update(
            "insert into exports (id, workspace_id, document_version_id, format, template_version, status) values (?, ?, ?, ?, 'ko-v1', 'REQUESTED')",
            export,
            s.workspaceId,
            versionId,
            format,
        )
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            job,
            s.workspaceId,
            DocumentExportJobHandler.TYPE,
            """{"exportId":"$export"}""",
        )
        return Requested(job, export)
    }

    private fun awaitStatus(
        job: UUID,
        status: String,
    ) = await().atMost(Duration.ofSeconds(20)).untilAsserted {
        assertThat(
            jdbc.queryForObject(
                "select status || coalesce(':' || error_code, '') from jobs where id = ?",
                String::class.java,
                job,
            ),
        ).isEqualTo(status)
    }
}
