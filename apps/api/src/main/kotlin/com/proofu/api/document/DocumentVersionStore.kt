package com.proofu.api.document

import com.proofu.domain.documents.ProvenanceRelation
import com.proofu.domain.documents.ProvenanceSourceType
import com.proofu.domain.documents.VersionAuthor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

/** document_versions and provenance_links are append-only, so they are read and written with plain SQL. */
@Repository
class DocumentVersionStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun latestId(documentId: UUID): UUID? =
        jdbc
            .query(
                "select id from document_versions where document_id = ? order by created_at desc, id desc limit 1",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                documentId,
            ).firstOrNull()

    fun latestIds(documentIds: Collection<UUID>): Map<UUID, Pair<UUID, VersionAuthor>> =
        if (documentIds.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .query(
                    """
                    select distinct on (document_id) document_id, id, created_by from document_versions
                    where document_id in (${documentIds.joinToString(",") { "?" }})
                    order by document_id, created_at desc, id desc
                    """.trimIndent(),
                    { rs, _ ->
                        rs.getObject("document_id", UUID::class.java) to
                            (rs.getObject("id", UUID::class.java) to VersionAuthor.valueOf(rs.getString("created_by")))
                    },
                    *documentIds.toTypedArray(),
                ).toMap()
        }

    fun find(
        versionId: UUID,
        workspaceId: UUID,
    ): DocumentVersionResponse? =
        jdbc
            .query(
                """
                select v.* from document_versions v join documents d on d.id = v.document_id
                where v.id = ? and d.workspace_id = ? and d.deleted_at is null
                """.trimIndent(),
                { rs, _ -> row(rs) },
                versionId,
                workspaceId,
            ).firstOrNull()
            ?.withProvenance()

    fun listForDocument(documentId: UUID): List<DocumentVersionResponse> =
        jdbc
            .query(
                "select * from document_versions where document_id = ? order by created_at desc, id desc",
                { rs, _ -> row(rs) },
                documentId,
            ).map { it.withProvenance() }

    fun insert(
        version: DocumentVersionResponse,
        sourceJobId: UUID? = null,
    ) {
        jdbc.update(
            """
            insert into document_versions
              (id, document_id, parent_id, label, content_json, template_version, prompt_version, model_ref, created_by, source_job_id)
            values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """.trimIndent(),
            version.id,
            version.documentId,
            version.parentId,
            version.label,
            mapper.writeValueAsString(mapOf("blocks" to version.blocks)),
            version.templateVersion,
            version.promptVersion,
            version.modelRef,
            version.createdBy.name,
            sourceJobId,
        )
        version.provenance.forEach { p ->
            jdbc.update(
                "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, ?, ?, ?, ?, ?)",
                version.id,
                p.blockId,
                p.sourceType.name,
                p.sourceId,
                p.sourceRevision,
                p.relation.name,
            )
        }
    }

    private fun DocumentVersionResponse.withProvenance() =
        copy(
            provenance =
                jdbc.query(
                    "select * from provenance_links where version_id = ? order by block_id, source_type, source_id",
                    { rs, _ ->
                        ProvenanceLinkResponse(
                            blockId = rs.getString("block_id"),
                            sourceType = ProvenanceSourceType.valueOf(rs.getString("source_type")),
                            sourceId = rs.getObject("source_id", UUID::class.java),
                            sourceRevision = rs.getLong("source_revision"),
                            relation = ProvenanceRelation.valueOf(rs.getString("relation")),
                        )
                    },
                    id,
                ),
        )

    private fun row(rs: ResultSet): DocumentVersionResponse {
        val content = mapper.readTree(rs.getString("content_json"))
        val blocks =
            content.get("blocks")?.let { mapper.treeToValue(it, Array<DocumentBlockDto>::class.java) } ?: emptyArray()
        return DocumentVersionResponse(
            id = rs.getObject("id", UUID::class.java),
            documentId = rs.getObject("document_id", UUID::class.java),
            parentId = rs.getObject("parent_id", UUID::class.java),
            label = rs.getString("label"),
            blocks = blocks.toList(),
            templateVersion = rs.getString("template_version"),
            promptVersion = rs.getString("prompt_version"),
            modelRef = rs.getString("model_ref"),
            createdBy = VersionAuthor.valueOf(rs.getString("created_by")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            provenance = emptyList(),
        )
    }
}
