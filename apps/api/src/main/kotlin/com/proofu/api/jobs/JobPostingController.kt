package com.proofu.api.jobs

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.DateIdCursor
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.V1)
class JobPostingController(
    private val service: JobPostingService,
) {
    @GetMapping("/job-postings")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): JobPostingPage = service.list(workspace, q, cursor?.let(DateIdCursor::decode), limit)

    @PostMapping("/job-postings/import")
    @ResponseStatus(HttpStatus.CREATED)
    fun import(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: ImportRequest,
    ): ImportResult = service.import(workspace, request)

    @GetMapping("/job-postings/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): JobPostingDetail = service.get(workspace, id)

    @DeleteMapping("/job-postings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)

    @GetMapping("/job-posting-snapshots/{id}")
    fun snapshot(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): SnapshotResponse = service.getSnapshot(workspace, id)
}
