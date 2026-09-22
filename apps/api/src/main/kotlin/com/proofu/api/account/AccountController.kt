package com.proofu.api.account

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.web.ApiPaths
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.V1)
class AccountController(
    private val accounts: AccountService,
) {
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun deleteMyAccount(workspace: WorkspaceContext): AcceptedJob = AcceptedJob(accounts.requestDeletion(workspace))

    @GetMapping("/me/exports")
    fun listExports(workspace: WorkspaceContext): AccountExportList = accounts.listExports(workspace)

    @PostMapping("/me/exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startExport(workspace: WorkspaceContext): AcceptedJob = AcceptedJob(accounts.requestExport(workspace))

    @GetMapping("/me/exports/{id}/file")
    fun downloadExport(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val file = accounts.exportFile(workspace, id)
        return ResponseEntity
            .ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(file.fileName)
                    .build()
                    .toString(),
            ).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(file.bytes)
    }
}
