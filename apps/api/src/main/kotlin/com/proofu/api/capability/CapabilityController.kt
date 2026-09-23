package com.proofu.api.capability

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.InvalidRequestException
import com.proofu.domain.career.CapabilityCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/capabilities")
class CapabilityController(
    private val service: CapabilityService,
) {
    @GetMapping
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) category: CapabilityCategory?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): CapabilityPage = service.list(workspace, category, cursor?.let(DateIdCursor::decode), limit)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: CapabilityRequest,
    ): CapabilityResponse = service.create(workspace, request)

    @GetMapping("/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): CapabilityResponse = service.get(workspace, id)

    @PatchMapping("/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CapabilityRequest,
    ): CapabilityResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)

    @PutMapping("/{id}/evidence")
    fun setEvidence(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CapabilityEvidenceRequest,
    ): CapabilityResponse = service.setEvidence(workspace, id, request)
}
