package com.proofu.worker.revision

import com.proofu.ai.revision.SentenceReviser
import com.proofu.ai.revision.SentenceToRevise
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.RevisionMode
import com.proofu.worker.documents.VersionContentReader
import com.proofu.worker.jobs.AiFailures
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * "문장 개선": propose a rewrite of one block. The proposal lives only in the job result; the
 * user applies it in the editor and saves a version, so references and certainty never move.
 */
@Component
class SentenceRevisionJobHandler(
    private val reviser: SentenceReviser,
    private val content: VersionContentReader,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) : JobHandler {
    private val log = LoggerFactory.getLogger(SentenceRevisionJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val versionId = UUID.fromString(payload.get("versionId").asString())
        val blockId = payload.get("blockId").asString()
        val mode = RevisionMode.valueOf(payload.get("mode").asString())
        val owned =
            jdbc.queryForObject(
                """
                select count(*) from document_versions v join documents d on d.id = v.document_id
                where v.id = ? and d.workspace_id = ? and d.deleted_at is null
                """.trimIndent(),
                Long::class.java,
                versionId,
                job.workspaceId,
            ) ?: 0L
        if (owned == 0L) throw JobFailure("VERSION_NOT_FOUND", "version $versionId", retryable = false)
        val block =
            content.output(versionId).blocks.firstOrNull { it.blockId == blockId }
                ?: throw JobFailure("BLOCK_NOT_FOUND", "block $blockId", retryable = false)
        val facts =
            if (block.claimRefs.isEmpty()) {
                emptyList()
            } else {
                jdbc.query(
                    "select text from claims where id in (${block.claimRefs.joinToString(
                        ",",
                    ) { "?" }}) and deleted_at is null",
                    { rs, _ -> rs.getString("text") },
                    *block.claimRefs.map { it.value }.toTypedArray(),
                )
            }
        val language =
            jdbc.queryForObject(
                "select d.language from document_versions v join documents d on d.id = v.document_id where v.id = ?",
                String::class.java,
                versionId,
            ) ?: "ko"

        val result =
            AiFailures.guard {
                reviser.revise(
                    WorkspaceId(job.workspaceId),
                    SentenceToRevise(blockId, block.text, facts, mode, language),
                    job.id,
                )
            }
        log.info(
            "document.revision version={} block={} mode={} accepted={} cost={}µ$",
            versionId,
            blockId,
            mode,
            result.revised != null,
            result.costMicros,
        )
        return mapper.writeValueAsString(
            mapOf(
                "versionId" to versionId.toString(),
                "blockId" to blockId,
                "mode" to mode.name,
                "original" to block.text,
                "revised" to result.revised,
                "changes" to result.changes,
                "rejected" to result.rejected,
                "costMicros" to result.costMicros,
            ),
        )
    }

    companion object {
        /** Must match apps/api JobTypes.DOCUMENT_REVISION. */
        const val TYPE = "document.revision"
    }
}
