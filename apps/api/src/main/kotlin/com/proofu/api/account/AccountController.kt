package com.proofu.api.account

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.web.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApiPaths.V1)
class AccountController(
    private val accounts: AccountService,
) {
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun deleteMyAccount(workspace: WorkspaceContext): AcceptedJob = AcceptedJob(accounts.requestDeletion(workspace))
}
