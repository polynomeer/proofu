package com.proofu.api.submission

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.V1)
class SubmissionController(
    private val submissions: SubmissionService,
) {
    @GetMapping("/applications/{id}/submissions")
    fun list(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): SubmissionList = submissions.list(workspace, id)

    @PostMapping("/applications/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateSubmissionRequest,
    ): SubmissionResponse = submissions.create(workspace, id, request)

    @GetMapping("/submission-snapshots/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): SubmissionDetailResponse = submissions.get(workspace, id)
}
