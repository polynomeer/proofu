package com.proofu.api.submission

import com.proofu.api.application.ApplicationService
import com.proofu.api.audit.AuditLog
import com.proofu.api.document.DocumentBlockDto
import com.proofu.api.document.DocumentRepository
import com.proofu.api.document.DocumentVersionStore
import com.proofu.api.document.TemplateSectionResponse
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.applications.SubmissionSnapshot
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.SubmissionSnapshotId
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.documents.VersionAuthor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/** submission_snapshots is append-only (ADR-0006): plain SQL, no entity, never updated. */
@Service
class SubmissionService(
    private val applications: ApplicationService,
    private val documents: DocumentRepository,
    private val versions: DocumentVersionStore,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        applicationId: UUID,
    ): SubmissionList {
        applications.get(workspace, applicationId)
        return SubmissionList(
            jdbc.query(
                "$SELECT where s.application_id = ? order by s.submitted_at desc, s.id desc",
                { rs, _ -> row(rs) },
                applicationId,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): SubmissionDetailResponse {
        val row =
            jdbc
                .query(
                    "$SELECT where s.id = ? and a.workspace_id = ? and a.deleted_at is null",
                    { rs, _ -> row(rs) to (rs.getString("company") to rs.getString("role_title")) },
                    id,
                    workspace.workspaceId.value,
                ).firstOrNull() ?: throw ResourceNotFoundException(TARGET, id)
        val (summary, posting) = row
        val version =
            versions.find(summary.documentVersionId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document version", summary.documentVersionId)
        return SubmissionDetailResponse(
            id = summary.id,
            applicationId = summary.applicationId,
            documentId = summary.documentId,
            documentTitle = summary.documentTitle,
            documentType = summary.documentType,
            documentVersionId = summary.documentVersionId,
            versionLabel = summary.versionLabel,
            versionCreatedBy = summary.versionCreatedBy,
            postingSnapshotId = summary.postingSnapshotId,
            hash = summary.hash,
            submittedAt = summary.submittedAt,
            createdAt = summary.createdAt,
            version = version,
            sections =
                (
                    DocumentTemplate.find(version.templateVersion, summary.documentType)
                        ?: DocumentTemplate.latest(summary.documentType)
                ).sections
                    .map(TemplateSectionResponse::from),
            company = posting.first,
            roleTitle = posting.second,
        )
    }

    /**
     * Freezes what was submitted and, from INTERESTED/PREPARING, moves the application to
     * SUBMITTED. INTERESTED walks through PREPARING so the event history stays honest.
     */
    @Transactional
    fun create(
        workspace: WorkspaceContext,
        applicationId: UUID,
        request: CreateSubmissionRequest,
    ): SubmissionResponse {
        val application = applications.lockForUpdate(workspace, applicationId)
        val versionId = requireNotNull(request.documentVersionId)
        val version =
            versions.find(versionId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document version", versionId)
        val document =
            documents.findByIdAndWorkspaceIdAndDeletedAtIsNull(version.documentId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document", version.documentId)
        val postingHash =
            jdbc.queryForObject(
                "select content_hash from job_posting_snapshots where id = ?",
                String::class.java,
                application.snapshotId,
            )
        val contentJson =
            jdbc.queryForObject(
                "select content_json::text from document_versions where id = ?",
                String::class.java,
                versionId,
            )
        val now = Instant.now(clock)
        val snapshot =
            SubmissionSnapshot.freeze(
                id = SubmissionSnapshotId(ids.next()),
                application = application.toDomain(),
                documentVersionId = DocumentVersionId(versionId),
                documentApplicationId = ApplicationId(document.applicationId),
                content = GeneratedOutput(version.blocks.map(DocumentBlockDto::toDomain)),
                contentJson = checkNotNull(contentJson),
                postingContentHash = checkNotNull(postingHash),
                submittedAt = request.submittedAt ?: now,
                now = now,
            )
        jdbc.update(
            """
            insert into submission_snapshots (id, application_id, document_version_id, posting_snapshot_id, hash, submitted_at)
            values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            snapshot.id.value,
            snapshot.applicationId.value,
            snapshot.documentVersionId.value,
            snapshot.postingSnapshotId.value,
            snapshot.hash,
            OffsetDateTime.ofInstant(snapshot.submittedAt, clock.zone),
        )
        val note = request.note?.trim()?.ifEmpty { null } ?: "제출 스냅샷 ${snapshot.id.value}"
        if (application.status == ApplicationStatus.INTERESTED) {
            applications.advance(workspace, application, ApplicationStatus.PREPARING, null)
        }
        if (application.status == ApplicationStatus.PREPARING) {
            applications.advance(workspace, application, ApplicationStatus.SUBMITTED, note)
        }
        val response =
            jdbc.query("$SELECT where s.id = ?", { rs, _ -> row(rs) }, snapshot.id.value).single()
        audit.record(workspace, "submission.created", TARGET, snapshot.id.value, after = response)
        return response
    }

    private fun row(rs: ResultSet) =
        SubmissionResponse(
            id = rs.getObject("id", UUID::class.java),
            applicationId = rs.getObject("application_id", UUID::class.java),
            documentId = rs.getObject("document_id", UUID::class.java),
            documentTitle = rs.getString("document_title"),
            documentType = DocumentType.valueOf(rs.getString("document_type")),
            documentVersionId = rs.getObject("document_version_id", UUID::class.java),
            versionLabel = rs.getString("version_label"),
            versionCreatedBy = VersionAuthor.valueOf(rs.getString("version_created_by")),
            postingSnapshotId = rs.getObject("posting_snapshot_id", UUID::class.java),
            hash = rs.getString("hash"),
            submittedAt = rs.getObject("submitted_at", OffsetDateTime::class.java).toInstant(),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        )

    private companion object {
        const val TARGET = "submission"
        val SELECT =
            """
            select s.id, s.application_id, s.document_version_id, s.posting_snapshot_id, s.hash, s.submitted_at, s.created_at,
                   d.id as document_id, d.title as document_title, d.type as document_type,
                   v.label as version_label, v.created_by as version_created_by,
                   a.company, a.role_title
            from submission_snapshots s
            join document_versions v on v.id = s.document_version_id
            join documents d on d.id = v.document_id
            join applications a on a.id = s.application_id
            """.trimIndent()
    }
}
