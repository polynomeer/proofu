package com.proofu.api.identity

import com.proofu.api.web.ApiPaths
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class ReauthenticationRequest(
    @field:NotBlank @field:Size(max = 8192) val idToken: String?,
)

@RestController
@RequestMapping(ApiPaths.V1)
class ReauthenticationController(
    private val reauthentications: ReauthenticationService,
) {
    @PostMapping("/me/reauthentications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun recordReauthentication(
        workspace: WorkspaceContext,
        @Valid @RequestBody body: ReauthenticationRequest,
    ) = reauthentications.record(workspace, body.idToken!!)
}
