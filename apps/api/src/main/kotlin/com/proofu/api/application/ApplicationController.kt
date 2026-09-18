package com.proofu.api.application

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.domain.applications.ApplicationStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/applications")
class ApplicationController(
    private val service: ApplicationService,
) {
    @GetMapping
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) status: ApplicationStatus?,
        @RequestParam(defaultValue = "true") includeTerminal: Boolean,
    ): ApplicationList = service.list(workspace, status, includeTerminal)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: CreateApplicationRequest,
    ): ApplicationResponse = service.create(workspace, request)

    @GetMapping("/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ApplicationDetail = service.get(workspace, id)

    @PatchMapping("/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateApplicationRequest,
    ): ApplicationResponse = service.update(workspace, id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)

    @PostMapping("/{id}/transitions")
    fun transition(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TransitionRequest,
    ): ApplicationDetail = service.transition(workspace, id, request)
}
