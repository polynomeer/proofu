package com.proofu.api.identity

import com.proofu.domain.common.UserId
import com.proofu.domain.common.WorkspaceId

/** The authenticated caller and the workspace every query in the request is scoped to. */
data class WorkspaceContext(
    val workspaceId: WorkspaceId,
    val userId: UserId,
)
