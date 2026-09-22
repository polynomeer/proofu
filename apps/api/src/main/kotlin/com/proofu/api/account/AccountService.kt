package com.proofu.api.account

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.AuthProperties
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Account deletion (docs/data/retention.md): the caller proves a recent login, the user and
 * workspace are marked deleted so the next request is refused, and the purge job does the rest.
 */
@Service
class AccountService(
    private val jdbc: JdbcTemplate,
    private val jobs: JobService,
    private val audit: AuditLog,
    private val auth: AuthProperties,
    private val clock: Clock,
) {
    @Transactional
    fun requestDeletion(workspace: WorkspaceContext): UUID {
        workspace.requireRecentAuthentication(clock, auth.reauthMaxAge)
        val userId = workspace.userId.value
        val workspaceId = workspace.workspaceId.value
        jdbc.update("update users set deleted_at = now() where id = ? and deleted_at is null", userId)
        jdbc.update("update workspaces set deleted_at = now() where id = ? and deleted_at is null", workspaceId)
        val jobId =
            jobs.enqueue(
                workspace,
                JobTypes.ACCOUNT_PURGE,
                mapOf("userId" to userId.toString(), "workspaceId" to workspaceId.toString()),
                dedupeKey = workspaceId.toString(),
            )
        audit.record(workspace, "account.deletion_requested", "workspace", workspaceId, after = mapOf("jobId" to jobId))
        return jobId
    }
}
