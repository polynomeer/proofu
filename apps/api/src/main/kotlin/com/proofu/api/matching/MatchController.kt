package com.proofu.api.matching

import com.proofu.api.application.ApplicationService
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ApiPaths
import com.proofu.domain.matching.MatchDecision
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class DecisionRequest(
    val decision: MatchDecision?,
)

@RestController
@RequestMapping(ApiPaths.V1)
class MatchController(
    private val reports: MatchReportService,
    private val applications: ApplicationService,
    private val jobs: JobService,
) {
    /** F05: score and explain; a pending run for the same application is returned instead of a duplicate. */
    @PostMapping("/applications/{id}/match-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startMatching(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): AcceptedJob {
        applications.get(workspace, id)
        return AcceptedJob(
            jobs.enqueue(
                workspace,
                JobTypes.APPLICATION_MATCH,
                mapOf("applicationId" to id.toString()),
                dedupeKey = id.toString(),
            ),
        )
    }

    @GetMapping("/applications/{id}/matches")
    fun report(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): MatchReport = reports.report(workspace, id)

    @PutMapping("/requirement-matches/{id}/decision")
    fun decide(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @RequestBody request: DecisionRequest,
    ): RequirementMatchResponse = reports.decide(workspace, id, request.decision)
}
