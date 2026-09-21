package com.proofu.api.document

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.DateIdCursor
import com.proofu.domain.documents.DocumentType
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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
class DocumentController(
    private val documents: DocumentService,
    private val ats: AtsCheckService,
) {
    @GetMapping("/document-versions/{id}/ats-check")
    fun atsCheck(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): AtsReportResponse = ats.check(workspace, id)

    @GetMapping("/documents")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
        @RequestParam(required = false) type: DocumentType?,
        @RequestParam(required = false) q: String?,
    ): DocumentPage =
        documents.list(workspace, type, q?.trim()?.ifEmpty { null }, cursor?.let(DateIdCursor::decode), limit)

    @GetMapping("/applications/{id}/documents")
    fun listForApplication(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): DocumentList = documents.listForApplication(workspace, id)

    @PostMapping("/applications/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateDocumentRequest,
    ): DocumentResponse = documents.create(workspace, id, request)

    @GetMapping("/documents/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): DocumentDetailResponse = documents.get(workspace, id)

    @GetMapping("/documents/{id}/versions")
    fun listVersions(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): DocumentVersionList = documents.listVersions(workspace, id)

    @PostMapping("/documents/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createVersion(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateVersionRequest,
    ): DocumentVersionResponse = documents.createUserVersion(workspace, id, request)

    @GetMapping("/document-versions/{id}")
    fun getVersion(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): DocumentVersionResponse = documents.getVersion(workspace, id)

    /** F06: draft a new AI version. A pending run for the same document is returned instead of a duplicate. */
    @PostMapping("/documents/{id}/generation-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startGeneration(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
        @RequestBody(required = false) request: GenerationRequest?,
    ): AcceptedJob = AcceptedJob(documents.startGeneration(workspace, id, request ?: GenerationRequest()))
}
