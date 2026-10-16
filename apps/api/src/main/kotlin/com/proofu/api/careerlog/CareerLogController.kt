package com.proofu.api.careerlog

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.InvalidRequestException
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
class CareerLogController(
    private val service: CareerLogService,
) {
    @GetMapping("/career-logs")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) promoted: Boolean?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): CareerLogPage = service.list(workspace, promoted, cursor?.let(DateIdCursor::decode), limit)

    @PostMapping("/career-logs")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: CareerLogRequest,
    ): CareerLogResponse = service.create(workspace, request)

    @GetMapping("/career-logs/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): CareerLogResponse = service.get(workspace, id)

    @PatchMapping("/career-logs/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CareerLogRequest,
    ): CareerLogResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    /** Records that this log became an achievement or evidence the caller already created. */
    @PostMapping("/career-logs/{id}/promotion")
    fun promote(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CareerLogPromotionRequest,
    ): CareerLogResponse = service.promote(workspace, id, request)

    @DeleteMapping("/career-logs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)
}
