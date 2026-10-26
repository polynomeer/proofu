package com.proofu.api.imports

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.InstantIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.imports.ImportItemStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * F21 가져오기, 1단계. The bundle goes to `import.plan` and comes back as a plan — nothing is
 * written to the workspace until the user picks from it, and the bundle itself is never stored
 * (docs/product/personal-archive-import.md §흐름).
 */
@Service
class ImportService(
    private val runs: ImportRunRepository,
    private val items: ImportItemRepository,
) {
    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ImportRunResponse {
        val run =
            runs.findByIdAndWorkspaceId(id, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, id)
        val rows = items.findByRunIdOrderByCreatedAtAsc(run.id)
        return ImportRunResponse(
            id = run.id,
            source = run.source,
            status = run.status,
            itemCount = run.itemCount,
            summary = rows.groupingBy { it.status }.eachCount(),
            items =
                rows.map {
                    ImportItemResponse(
                        id = it.id,
                        sourceKey = it.sourceKey,
                        sourcePath = it.sourcePath,
                        type = it.type,
                        status = it.status,
                        reason = it.reason,
                        targetType = it.targetType,
                        targetId = it.targetId,
                        appliedAt = it.appliedAt,
                    )
                },
            createdAt = checkNotNull(run.createdAt),
            appliedAt = run.appliedAt,
        )
    }

    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        cursor: InstantIdCursor?,
        limit: Int,
    ): ImportRunPage {
        val rows = runs.page(workspace.workspaceId.value, cursor?.at, cursor?.id, limit + 1)
        val page = rows.take(limit)
        val next =
            if (rows.size > limit) {
                InstantIdCursor(checkNotNull(page.last().createdAt), page.last().id).encode()
            } else {
                null
            }
        return ImportRunPage(
            page.map {
                ImportRunSummary(
                    id = it.id,
                    source = it.source,
                    status = it.status,
                    itemCount = it.itemCount,
                    createdAt = checkNotNull(it.createdAt),
                    appliedAt = it.appliedAt,
                )
            },
            next,
        )
    }

    /** What the screen opens on: how many items reached each verdict. */
    fun summaryOf(response: ImportRunResponse): Map<ImportItemStatus, Int> = response.summary

    private companion object {
        const val TARGET = "import_run"
    }
}

@RestController
@RequestMapping(ApiPaths.V1)
class ImportController(
    private val jobs: JobService,
    private val service: ImportService,
) {
    /** Hands the bundle to `import.plan`. Writes nothing to the workspace. */
    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun plan(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: ImportBundleRequest,
    ): AcceptedJob =
        AcceptedJob(
            jobs.enqueue(
                workspace,
                type = JobTypes.IMPORT_PLAN,
                payload =
                    mapOf(
                        "source" to request.source,
                        "items" to
                            requireNotNull(request.items).map {
                                mapOf(
                                    "sourceKey" to requireNotNull(it.sourceKey).trim(),
                                    "type" to requireNotNull(it.type).name,
                                    "sourcePath" to it.sourcePath,
                                    "fields" to it.fields,
                                    "links" to it.links,
                                )
                            },
                        "contents" to request.contents,
                    ),
            ),
        )

    @GetMapping("/imports")
    fun list(
        workspace: WorkspaceContext,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ImportRunPage = service.list(workspace, cursor?.let(InstantIdCursor::decode), limit)

    @GetMapping("/imports/{id}")
    fun get(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): ImportRunResponse = service.get(workspace, id)
}
