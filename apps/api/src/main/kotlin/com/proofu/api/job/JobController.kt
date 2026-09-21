package com.proofu.api.job

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.jobs.JobPostingService
import com.proofu.api.web.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AcceptedJob(
    val jobId: UUID,
)

@RestController
@RequestMapping(ApiPaths.V1)
class JobController(
    private val jobs: JobService,
    private val postings: JobPostingService,
) {
    @GetMapping("/jobs/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): JobResponse = jobs.get(workspace, id)

    /** F04: extract requirements from a snapshot. Re-requests while one is pending return the same job. */
    @PostMapping("/job-posting-snapshots/{id}/analysis-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startAnalysis(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): AcceptedJob {
        val snapshot = postings.getSnapshot(workspace, id)
        val jobId =
            jobs.enqueue(
                workspace,
                type = JobTypes.POSTING_ANALYSIS,
                payload = mapOf("snapshotId" to snapshot.id.toString()),
                dedupeKey = snapshot.id.toString(),
            )
        return AcceptedJob(jobId)
    }
}

/** Job type names shared with the worker's handlers; keep in sync with apps/worker. */
object JobTypes {
    const val POSTING_ANALYSIS = "posting.analysis"
    const val APPLICATION_MATCH = "application.match"
    const val DOCUMENT_GENERATION = "document.generation"
    const val DOCUMENT_EXPORT = "document.export"
    const val DOCUMENT_REVISION = "document.revision"
}
