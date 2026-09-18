package com.proofu.api.jobs

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.InvalidRequestException
import com.proofu.api.web.NotImplementedException
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.events.JobPostingSnapshotCreated
import com.proofu.domain.jobs.JobPosting
import com.proofu.domain.jobs.JobPostingSnapshot
import com.proofu.domain.jobs.SnapshotSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class JobPostingService(
    private val postings: JobPostingRepository,
    private val snapshots: JobPostingSnapshotRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
    private val events: ApplicationEventPublisher,
) {
    /**
     * Manual import: reuse the posting that already tracks the same url (if any), then capture a
     * snapshot unless this exact content is already captured. URL fetching and career-ops are
     * contract-only until their integrations exist.
     */
    @Transactional
    fun import(
        workspace: WorkspaceContext,
        request: ImportRequest,
    ): ImportResult =
        when (requireNotNull(request.source)) {
            SnapshotSource.MANUAL_TEXT -> importManual(workspace, request)
            SnapshotSource.URL_FETCH -> throw NotImplementedException("URL fetching")
            SnapshotSource.CAREER_OPS -> throw NotImplementedException("career-ops import")
        }

    private fun importManual(
        workspace: WorkspaceContext,
        request: ImportRequest,
    ): ImportResult {
        val missing =
            listOf("text" to request.text, "company" to request.company, "roleTitle" to request.roleTitle)
                .filter { it.second.isNullOrBlank() }
                .associate { it.first to "필수 항목입니다" }
        if (missing.isNotEmpty()) {
            throw InvalidRequestException(
                "manual import needs text, company and roleTitle",
                missing,
            )
        }

        val url = request.sourceUrl?.trim()?.ifEmpty { null }
        val existing = url?.let { postings.lockByCanonicalUrl(workspace.workspaceId.value, it) }
        val posting =
            existing ?: postings
                .saveAndFlush(
                    JobPostingEntity.from(
                        JobPosting(
                            id = JobPostingId(ids.next()),
                            workspaceId = workspace.workspaceId,
                            company = requireNotNull(request.company).trim(),
                            roleTitle = requireNotNull(request.roleTitle).trim(),
                            canonicalUrl = url,
                            publishedAt = request.publishedAt,
                        ),
                    ),
                ).also {
                    audit.record(
                        workspace,
                        "job_posting.created",
                        POSTING,
                        it.id,
                        after = JobPostingResponse.from(it, emptyList()),
                    )
                }

        val captured =
            JobPostingSnapshot.capture(
                id = JobPostingSnapshotId(ids.next()),
                postingId = JobPostingId(posting.id),
                source = SnapshotSource.MANUAL_TEXT,
                text = requireNotNull(request.text),
                capturedAt = Instant.now(clock),
                sourceUrl = url,
            )
        snapshots.findByPostingIdAndContentHash(posting.id, captured.contentHash)?.let { same ->
            return ImportResult(posting.id, same.id, same.contentHash, same.capturedAt, snapshotCreated = false)
        }
        val saved = snapshots.saveAndFlush(JobPostingSnapshotEntity.from(captured))
        jdbc.update("update job_postings set updated_at = now() where id = ?", posting.id)
        audit.record(workspace, "job_posting_snapshot.created", SNAPSHOT, saved.id, after = SnapshotSummary.from(saved))
        events.publishEvent(
            JobPostingSnapshotCreated(
                ids.next(),
                saved.capturedAt,
                JobPostingSnapshotId(saved.id),
                saved.source,
                saved.contentHash,
            ),
        )
        return ImportResult(posting.id, saved.id, saved.contentHash, saved.capturedAt, snapshotCreated = true)
    }

    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        q: String?,
        cursor: DateIdCursor?,
        limit: Int,
    ): JobPostingPage {
        val rows =
            postings.page(
                workspace.workspaceId.value,
                q?.trim()?.ifEmpty { null },
                cursor?.date,
                cursor?.id,
                limit + 1,
            )
        val page = rows.take(limit)
        val next =
            if (rows.size > limit) {
                val last = page.last()
                DateIdCursor(checkNotNull(last.updatedAt).atZone(ZoneOffset.UTC).toLocalDate(), last.id).encode()
            } else {
                null
            }
        val byPosting =
            snapshots
                .findAllByPostingIdInOrderByCapturedAtDescIdDesc(
                    page.map { it.id },
                ).groupBy { it.postingId }
        return JobPostingPage(page.map { JobPostingResponse.from(it, byPosting[it.id] ?: emptyList()) }, next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): JobPostingDetail {
        val posting = find(workspace, id)
        return JobPostingDetail.from(
            posting,
            snapshots.findAllByPostingIdInOrderByCapturedAtDescIdDesc(listOf(posting.id)),
        )
    }

    @Transactional(readOnly = true)
    fun getSnapshot(
        workspace: WorkspaceContext,
        id: UUID,
    ): SnapshotResponse =
        snapshots.findByIdInWorkspace(id, workspace.workspaceId.value)?.let(SnapshotResponse::from)
            ?: throw ResourceNotFoundException(SNAPSHOT, id)

    /** Soft delete of the posting only; snapshots are immutable and stay for reproducibility. */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val posting =
            postings.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(POSTING, id)
        val before = JobPostingResponse.from(posting, emptyList())
        posting.deletedAt = Instant.now(clock)
        postings.saveAndFlush(posting)
        audit.record(workspace, "job_posting.deleted", POSTING, posting.id, before = before)
    }

    fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): JobPostingEntity =
        postings.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(POSTING, id)

    private companion object {
        const val POSTING = "job_posting"
        const val SNAPSHOT = "job_posting_snapshot"
    }
}
