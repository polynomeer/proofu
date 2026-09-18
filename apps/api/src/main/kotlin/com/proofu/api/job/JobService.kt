package com.proofu.api.job

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.common.IdGenerator
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class JobResponse(
    val id: UUID,
    val type: String,
    val status: String,
    val attempts: Int,
    val errorCode: String?,
    val result: JsonNode?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

/** Asynchronous work accepted with 202 and polled at GET /jobs/{id} (ADR-0004). */
@Service
class JobService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val ids: IdGenerator,
) {
    /**
     * Enqueues [type] with [payload]. When [dedupeKey] is given and a job of the same type with
     * that key is still queued or running, that job is returned instead of a duplicate.
     */
    @Transactional
    fun enqueue(
        workspace: WorkspaceContext,
        type: String,
        payload: Map<String, Any?>,
        dedupeKey: String? = null,
    ): UUID {
        if (dedupeKey != null) {
            jdbc
                .query(
                    """
                    select id from jobs
                    where workspace_id = ? and type = ? and status in ('QUEUED', 'RUNNING') and payload ->> 'dedupeKey' = ?
                    order by created_at desc limit 1
                    """.trimIndent(),
                    { rs, _ -> rs.getObject("id", UUID::class.java) },
                    workspace.workspaceId.value,
                    type,
                    dedupeKey,
                ).firstOrNull()
                ?.let { return it }
        }
        val id = ids.next()
        val body =
            payload + mapOf("workspaceId" to workspace.workspaceId.value.toString()) +
                (dedupeKey?.let { mapOf("dedupeKey" to it) } ?: emptyMap())
        jdbc.update(
            "insert into jobs (id, workspace_id, type, payload) values (?, ?, ?, ?::jsonb)",
            id,
            workspace.workspaceId.value,
            type,
            mapper.writeValueAsString(body),
        )
        return id
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): JobResponse =
        jdbc
            .query(
                """
                select id, type, status, attempts, error_code, result::text as result, created_at, started_at, finished_at
                from jobs where id = ? and workspace_id = ?
                """.trimIndent(),
                { rs, _ ->
                    JobResponse(
                        id = rs.getObject("id", UUID::class.java),
                        type = rs.getString("type"),
                        status = rs.getString("status"),
                        attempts = rs.getInt("attempts"),
                        errorCode = rs.getString("error_code"),
                        result = rs.getString("result")?.let(mapper::readTree),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                        startedAt = rs.getTimestamp("started_at")?.toInstant(),
                        finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                    )
                },
                id,
                workspace.workspaceId.value,
            ).firstOrNull() ?: throw ResourceNotFoundException("job", id)
}
