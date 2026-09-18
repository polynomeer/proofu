package com.proofu.api.review

import com.proofu.api.application.ApplicationService
import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.ReviewId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReviewService(
    private val repository: ReviewRepository,
    private val applications: ApplicationService,
    private val ids: IdGenerator,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun listForApplication(
        workspace: WorkspaceContext,
        applicationId: UUID,
    ): ReviewList {
        applications.get(workspace, applicationId)
        return ReviewList(
            repository.findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId).map(ReviewResponse::from),
        )
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ReviewResponse = ReviewResponse.from(find(workspace, id))

    /**
     * Writing the first review is how DOCUMENT_REJECTED / NO_RESPONSE applications reach
     * REVIEWED: the status walks through REVIEW_PENDING so the history stays honest.
     */
    @Transactional
    fun create(
        workspace: WorkspaceContext,
        applicationId: UUID,
        request: ReviewRequest,
    ): ReviewResponse {
        val application = applications.lockForUpdate(workspace, applicationId)
        if (!application.status.acceptsReview) {
            throw DomainRuleViolation(
                "a review can only be written after a document result: current status is ${application.status}",
            )
        }
        val saved =
            repository.saveAndFlush(
                ReviewEntity.from(request.toDomain(ReviewId(ids.next()), ApplicationId(application.id))),
            )
        if (application.status == ApplicationStatus.DOCUMENT_REJECTED ||
            application.status == ApplicationStatus.NO_RESPONSE
        ) {
            applications.advance(workspace, application, ApplicationStatus.REVIEW_PENDING, null)
        }
        if (application.status == ApplicationStatus.REVIEW_PENDING) {
            applications.advance(workspace, application, ApplicationStatus.REVIEWED, "회고 기록")
        }
        val response = ReviewResponse.from(saved)
        audit.record(workspace, "review.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: ReviewRequest,
        expectedVersion: Long,
    ): ReviewResponse {
        val entity =
            repository.lockByIdInWorkspace(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        if (entity.version != expectedVersion) throw StaleVersionException(TARGET, expectedVersion, entity.version)
        val before = ReviewResponse.from(entity)
        entity.apply(request.toDomain(ReviewId(entity.id), ApplicationId(entity.applicationId)))
        entity.version += 1
        val saved = repository.saveAndFlush(entity)
        val response = ReviewResponse.from(saved)
        audit.record(workspace, "review.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity =
            repository.lockByIdInWorkspace(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val before = ReviewResponse.from(entity)
        repository.delete(entity)
        repository.flush()
        audit.record(workspace, "review.deleted", TARGET, entity.id, before = before)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ReviewEntity =
        repository.findByIdInWorkspace(id, workspace.workspaceId.value) ?: throw ResourceNotFoundException(TARGET, id)

    private companion object {
        const val TARGET = "review"
    }
}
