package com.proofu.api.settings

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
class SettingsController(
    private val settings: SettingsService,
) {
    @GetMapping("/me/settings")
    fun get(workspace: WorkspaceContext): SettingsResponse = settings.get(workspace)

    @PutMapping("/me/settings")
    fun save(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: SettingsRequest,
    ): SettingsResponse = settings.save(workspace, request)
}
