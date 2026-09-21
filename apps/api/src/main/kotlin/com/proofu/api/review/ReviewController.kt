package com.proofu.api.review

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.InvalidRequestException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.V1)
class ReviewController(
    private val service: ReviewService,
    private val context: ReviewContextService,
) {
    @GetMapping("/applications/{applicationId}/review-context")
    fun context(
        workspace: WorkspaceContext,
        @PathVariable applicationId: UUID,
    ): ReviewContext = context.context(workspace, applicationId)

    @GetMapping("/applications/{applicationId}/reviews")
    fun list(
        workspace: WorkspaceContext,
        @PathVariable applicationId: UUID,
    ): ReviewList = service.listForApplication(workspace, applicationId)

    @PostMapping("/applications/{applicationId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @PathVariable applicationId: UUID,
        @Valid @RequestBody request: ReviewRequest,
    ): ReviewResponse = service.create(workspace, applicationId, request)

    @GetMapping("/reviews/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ReviewResponse = service.get(workspace, id)

    @PatchMapping("/reviews/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReviewRequest,
    ): ReviewResponse {
        val version = request.version ?: throw InvalidRequestException("version is required")
        return service.update(workspace, id, request, version)
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)
}
