package com.proofu.api.achievement

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
class AchievementController(
    private val service: AchievementService,
) {
    @GetMapping("/projects/{projectId}/achievements")
    fun list(
        workspace: WorkspaceContext,
        @PathVariable projectId: UUID,
    ): AchievementList = service.listForProject(workspace, projectId)

    @PostMapping("/projects/{projectId}/achievements")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: AchievementRequest,
    ): AchievementResponse = service.create(workspace, projectId, request)

    @GetMapping("/achievements/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): AchievementResponse = service.get(workspace, id)

    @PatchMapping("/achievements/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AchievementRequest,
    ): AchievementResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    @DeleteMapping("/achievements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)
}
