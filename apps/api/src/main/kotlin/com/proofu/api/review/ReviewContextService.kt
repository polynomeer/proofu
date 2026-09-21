package com.proofu.api.review

import com.proofu.api.application.ApplicationService
import com.proofu.api.document.DocumentBlockDto
import com.proofu.api.document.DocumentVersionStore
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.matching.MatchReportService
import com.proofu.api.submission.SubmissionResponse
import com.proofu.api.submission.SubmissionService
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.applications.SubmissionFacts
import com.proofu.domain.common.RequirementId
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.jobs.RequirementCategory
import com.proofu.domain.matching.MatchDecision
import com.proofu.domain.matching.RequirementAssessment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ReviewPostingSnapshot(
    val id: UUID,
    val postingId: UUID,
    val capturedAt: Instant,
    val approvedRequirementCount: Int,
)

data class ReviewDocumentFacts(
    val totalBlocks: Int,
    val blocksWithClaims: Int,
    val supportedBlocks: Int,
    val approvedWithoutEvidence: Int,
    val evidenceLinkRate: Int,
    val requirementCoverage: Int,
)

data class ReviewRequirementFact(
    val id: UUID,
    val category: RequirementCategory,
    val text: String,
    val assessment: RequirementAssessment?,
    val acceptedCandidates: Int,
    val addressedBySubmission: Boolean,
)

data class ReviewContext(
    val applicationId: UUID,
    val status: ApplicationStatus,
    val postingSnapshot: ReviewPostingSnapshot,
    val submissionCount: Int,
    val submission: SubmissionResponse?,
    val document: ReviewDocumentFacts?,
    val matchRunAt: Instant?,
    val requirements: List<ReviewRequirementFact>,
)

/** A02 comparison material, assembled from read models that already exist; nothing is inferred here. */
@Service
class ReviewContextService(
    private val applications: ApplicationService,
    private val matches: MatchReportService,
    private val submissions: SubmissionService,
    private val versions: DocumentVersionStore,
) {
    @Transactional(readOnly = true)
    fun context(
        workspace: WorkspaceContext,
        applicationId: UUID,
    ): ReviewContext {
        val application = applications.get(workspace, applicationId)
        val report = matches.report(workspace, applicationId)
        val submitted = submissions.list(workspace, applicationId).items
        val latest = submitted.firstOrNull()
        val approvedIds = report.groups.map { RequirementId(it.requirement.id) }.toSet()
        val facts =
            latest
                ?.let { versions.find(it.documentVersionId, workspace.workspaceId.value) }
                ?.let { SubmissionFacts.of(GeneratedOutput(it.blocks.map(DocumentBlockDto::toDomain)), approvedIds) }

        return ReviewContext(
            applicationId = application.id,
            status = application.status,
            postingSnapshot =
                ReviewPostingSnapshot(
                    id = application.snapshotId,
                    postingId = application.postingId,
                    capturedAt = application.snapshot.capturedAt,
                    approvedRequirementCount = report.groups.size,
                ),
            submissionCount = submitted.size,
            submission = latest,
            document =
                facts?.let {
                    ReviewDocumentFacts(
                        totalBlocks = it.totalBlocks,
                        blocksWithClaims = it.blocksWithClaims,
                        supportedBlocks = it.supportedBlocks,
                        approvedWithoutEvidence = it.approvedWithoutEvidence,
                        evidenceLinkRate = it.evidenceLinkRate,
                        requirementCoverage = it.requirementCoverage,
                    )
                },
            matchRunAt = report.lastRunAt,
            requirements =
                report.groups.map { g ->
                    ReviewRequirementFact(
                        id = g.requirement.id,
                        category = g.requirement.category,
                        text = g.requirement.text,
                        assessment = g.assessment,
                        acceptedCandidates = g.candidates.count { it.userDecision == MatchDecision.ACCEPTED },
                        addressedBySubmission =
                            facts?.addressedRequirements?.contains(RequirementId(g.requirement.id)) ?: false,
                    )
                },
        )
    }
}
