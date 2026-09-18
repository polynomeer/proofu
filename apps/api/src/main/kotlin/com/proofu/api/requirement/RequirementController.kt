package com.proofu.api.requirement

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
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
class RequirementController(
    private val service: RequirementService,
) {
    @GetMapping("/job-posting-snapshots/{snapshotId}/requirements")
    fun list(
        workspace: WorkspaceContext,
        @PathVariable snapshotId: UUID,
    ): RequirementList = service.listForSnapshot(workspace, snapshotId)

    @PostMapping("/job-posting-snapshots/{snapshotId}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @PathVariable snapshotId: UUID,
        @Valid @RequestBody request: CreateRequirementRequest,
    ): RequirementResponse = service.create(workspace, snapshotId, request)

    @GetMapping("/requirements/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): RequirementResponse = service.get(workspace, id)

    @PatchMapping("/requirements/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRequirementRequest,
    ): RequirementResponse = service.update(workspace, id, request)

    @DeleteMapping("/requirements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)
}
