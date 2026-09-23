package com.proofu.api.skill

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.InvalidRequestException
import com.proofu.domain.career.SkillCategory
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
@RequestMapping(ApiPaths.V1)
class SkillController(
    private val service: SkillService,
) {
    @GetMapping("/skills")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) category: SkillCategory?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): SkillPage = service.list(workspace, category, cursor?.let(DateIdCursor::decode), limit)

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: SkillRequest,
    ): SkillResponse = service.create(workspace, request)

    @GetMapping("/skills/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): SkillResponse = service.get(workspace, id)

    @PatchMapping("/skills/{id}")
    fun update(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: SkillRequest,
    ): SkillResponse {
        val revision = request.revision ?: throw InvalidRequestException("revision is required")
        return service.update(workspace, id, request, revision)
    }

    @DeleteMapping("/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ) = service.delete(workspace, id)

    @GetMapping("/projects/{id}/skills")
    fun ofProject(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): SkillList = service.ofProject(workspace, id)

    @PutMapping("/projects/{id}/skills")
    fun setProjectSkills(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ProjectSkillsRequest,
    ): SkillList = service.setProjectSkills(workspace, id, request)
}
