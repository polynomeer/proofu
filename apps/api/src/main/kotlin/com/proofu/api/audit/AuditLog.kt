package com.proofu.api.audit

import com.proofu.api.identity.WorkspaceContext
import com.proofu.domain.common.IdGenerator
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID

/**
 * Appends to audit_events. Only hashes of the before/after state are stored so the
 * audit trail never carries career text or other personal data.
 */
@Component
class AuditLog(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val ids: IdGenerator,
) {
    fun record(
        workspace: WorkspaceContext,
        action: String,
        targetType: String,
        targetId: UUID,
        before: Any? = null,
        after: Any? = null,
    ) {
        jdbc.update(
            """
            insert into audit_events (id, workspace_id, actor_id, actor_type, action, target_type, target_id, before_hash, after_hash)
            values (?, ?, ?, 'USER', ?, ?, ?, ?, ?)
            """.trimIndent(),
            ids.next(),
            workspace.workspaceId.value,
            workspace.userId.value,
            action,
            targetType,
            targetId,
            before?.let(::hash),
            after?.let(::hash),
        )
    }

    private fun hash(value: Any): String {
        val bytes = mapper.writeValueAsBytes(value)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
