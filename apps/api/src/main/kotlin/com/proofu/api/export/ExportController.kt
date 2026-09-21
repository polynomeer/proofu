package com.proofu.api.export

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.V1)
class ExportController(
    private val exports: ExportService,
) {
    @GetMapping("/document-versions/{id}/exports")
    fun list(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ExportList = exports.list(workspace, id)

    /** F07: render a version. A pending job for the same version and format is returned instead of a duplicate. */
    @PostMapping("/document-versions/{id}/exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: StartExportRequest,
    ): ExportAccepted = exports.start(workspace, id, requireNotNull(request.format))

    @GetMapping("/exports/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ExportResponse = exports.get(workspace, id)

    @GetMapping("/exports/{id}/file")
    fun download(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val file = exports.file(workspace, id)
        val disposition =
            ContentDisposition
                .attachment()
                .filename(file.fileName, StandardCharsets.UTF_8)
                .build()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .contentType(MediaType.parseMediaType(file.mimeType))
            .body(file.bytes)
    }
}
