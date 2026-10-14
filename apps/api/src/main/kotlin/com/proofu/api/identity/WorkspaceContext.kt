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
    /** `auth_time` from the access token, when the provider puts it there (Keycloak does). */
    val authenticatedAt: Instant? = null,
    /** The last fresh login the API verified itself from an ID token (ADR-0010 결정 3). */
    val reauthenticatedAt: Instant? = null,
    /** False only in the header auth mode (local and test), which has no login to prove. */
    val provesLogin: Boolean = false,
) {
    /**
     * Step-up for sensitive actions (ADR-0010 결정 1 §4, 결정 3): the latest login known — the
     * access token's `auth_time` or the verified re-authentication — must be recent. With
     * neither, the caller is asked to log in again.
     */
    fun requireRecentAuthentication(
        clock: Clock,
        maxAge: Duration,
    ) {
        if (!provesLogin) return
        val latest = listOfNotNull(authenticatedAt, reauthenticatedAt).maxOrNull()
        if (latest == null || latest.plus(maxAge).isBefore(Instant.now(clock))) {
            throw ReauthenticationRequiredException(maxAge)
        }
    }
}

class ReauthenticationRequiredException(
    val maxAge: Duration,
) : RuntimeException("this action needs a login newer than ${maxAge.toMinutes()} minutes")
