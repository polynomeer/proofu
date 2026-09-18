package com.proofu.api.claim

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.InvalidRequestException
import com.proofu.domain.evidence.ClaimSourceType
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
@RequestMapping(ApiPaths.V1)
class ClaimController(
    private val service: ClaimService,
) {
    @GetMapping("/claims")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) sourceType: ClaimSourceType?,
        @RequestParam(required = false) sourceId: UUID?,
        @RequestParam(required = false) projectId: UUID?,
    ): ClaimList = service.list(workspace, sourceType, sourceId, projectId)

    @GetMapping("/evidence/{id}/claims")
    fun listForEvidence(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ClaimList = service.listForEvidence(workspace, id)

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: ClaimRequest,
    ): ClaimResponse = service.create(workspace, request)

    @GetMapping("/claims/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ClaimResponse = service.get(workspace, id)

    @PatchMapping("/claims/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ClaimRequest,
    ): ClaimResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    @DeleteMapping("/claims/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)

    @PostMapping("/claims/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    fun link(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: LinkEvidenceRequest,
    ): ClaimResponse = service.link(workspace, id, request)

    @DeleteMapping("/claims/{id}/evidence/{evidenceId}")
    fun unlink(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @PathVariable evidenceId: UUID,
    ): ClaimResponse = service.unlink(workspace, id, evidenceId)
}
