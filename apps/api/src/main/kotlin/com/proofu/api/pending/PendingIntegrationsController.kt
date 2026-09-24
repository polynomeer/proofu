package com.proofu.api.pending

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ApiPaths
import com.proofu.api.web.NotImplementedException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Contract paths whose integration is still an open decision. They answer `501 NOT_IMPLEMENTED`
 * with Problem Details rather than 404, so a generated client gets a truthful "not yet" instead
 * of "no such endpoint" — and the contract and the runtime document stay in step
 * (`scripts/contract-diff.sh`). Each disappears when its decision lands.
 */
@RestController
@RequestMapping(ApiPaths.V1)
class PendingIntegrationsController {
    /** Needs the object store (docs/project/open-decisions.md: 내보내기·파일 저장소). */
    @PostMapping("/files/upload-sessions")
    fun createUploadSession(workspace: WorkspaceContext): Nothing =
        throw NotImplementedException("file upload (object storage is not chosen yet)")

    /** Needs the iterview contract version (docs/project/open-decisions.md). */
    @PostMapping("/applications/{id}/interview-handoffs")
    fun requestInterviewHandoff(
        workspace: WorkspaceContext,
        @PathVariable id: UUID,
    ): Nothing = throw NotImplementedException("interview handoff (the iterview contract is not agreed yet)")
}
