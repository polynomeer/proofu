package com.proofu.api.identity

import com.proofu.domain.common.UserId
import com.proofu.domain.common.WorkspaceId
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The authenticated caller and the workspace every query in the request is scoped to. */
data class WorkspaceContext(
    val workspaceId: WorkspaceId,
    val userId: UserId,
    /** When the user last proved who they are (`auth_time`); null when the auth mode cannot say. */
    val authenticatedAt: Instant? = null,
) {
    /**
     * Step-up for sensitive actions (ADR-0010 결정 1 §4): the login must be recent. Auth modes
     * without an `auth_time` (the interim header) are not asked to prove it.
     */
    fun requireRecentAuthentication(
        clock: Clock,
        maxAge: Duration,
    ) {
        val at = authenticatedAt ?: return
        if (at.plus(maxAge).isBefore(Instant.now(clock))) throw ReauthenticationRequiredException(maxAge)
    }
}

class ReauthenticationRequiredException(
    val maxAge: Duration,
) : RuntimeException("this action needs a login newer than ${maxAge.toMinutes()} minutes")
