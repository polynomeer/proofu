package com.proofu.api.profile

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApiPaths.V1)
class ProfileController(
    private val profiles: ProfileService,
) {
    @GetMapping("/me/profile")
    fun get(workspace: WorkspaceContext): ProfileResponse = profiles.get(workspace)

    @PutMapping("/me/profile")
    fun save(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: ProfileRequest,
    ): ProfileResponse = profiles.save(workspace, request)
}
