package com.proofu.api.document

import com.proofu.api.export.ExportService
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.requirement.RequirementService
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.documents.AtsChecker
import com.proofu.domain.documents.AtsSeverity
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.ExportStatus
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.jobs.RequirementStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class AtsFindingResponse(
    val code: String,
    val severity: AtsSeverity,
    val message: String,
    val details: List<String>,
)

data class AtsReportResponse(
    val documentVersionId: UUID,
    val warnings: Int,
    val findings: List<AtsFindingResponse>,
)

/** Runs the domain ATS checks on a stored version with the facts the API can see (requirements, READY exports). */
@Service
class AtsCheckService(
    private val versions: DocumentVersionStore,
    private val documents: DocumentRepository,
    private val requirements: RequirementService,
    private val exports: ExportService,
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun check(
        workspace: WorkspaceContext,
        versionId: UUID,
    ): AtsReportResponse {
        val version =
            versions.find(versionId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document version", versionId)
        val document =
            documents.findByIdAndWorkspaceIdAndDeletedAtIsNull(version.documentId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException("document", version.documentId)
        val snapshotId =
            jdbc.queryForObject(
                "select snapshot_id from applications where id = ?",
                UUID::class.java,
                document.applicationId,
            )
        val approved =
            requirements
                .listForSnapshot(workspace, checkNotNull(snapshotId))
                .items
                .filter { it.status == RequirementStatus.APPROVED }
                .map { it.text }
        val ready = exports.list(workspace, versionId).items.filter { it.status == ExportStatus.READY }
        val template =
            DocumentTemplate.find(version.templateVersion, document.type) ?: DocumentTemplate.latest(document.type)
        val report =
            AtsChecker.check(
                template = template,
                output = GeneratedOutput(version.blocks.map(DocumentBlockDto::toDomain)),
                approvedRequirements = approved,
                readyFormats = ready.map { it.format }.toSet(),
                pageCount = ready.firstOrNull { it.format == ExportFormat.PDF }?.pageCount,
            )
        return AtsReportResponse(
            documentVersionId = versionId,
            warnings = report.warnings,
            findings = report.findings.map { AtsFindingResponse(it.code, it.severity, it.message, it.details) },
        )
    }
}
