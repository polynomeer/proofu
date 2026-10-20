package com.proofu.api.contact

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.InstantIdCursor
import com.proofu.api.web.InvalidRequestException
import com.proofu.domain.career.ContactRelation
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
class ContactController(
    private val service: ContactService,
) {
    @GetMapping("/contacts")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) relation: ContactRelation?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ContactPage = service.list(workspace, relation, cursor?.let(InstantIdCursor::decode), limit)

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: ContactRequest,
    ): ContactResponse = service.create(workspace, request)

    @GetMapping("/contacts/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ContactResponse = service.get(workspace, id)

    @PatchMapping("/contacts/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ContactRequest,
    ): ContactResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)
}
