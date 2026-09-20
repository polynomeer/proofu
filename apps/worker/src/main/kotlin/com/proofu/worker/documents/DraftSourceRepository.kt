package com.proofu.worker.documents

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class DocumentRow(
    val id: UUID,
    val type: String,
    val language: String,
    val applicationId: UUID,
    val snapshotId: UUID,
    val company: String,
    val roleTitle: String,
    val latestVersionId: UUID?,
)

data class EvidenceRow(
    val id: UUID,
    val title: String,
    val revision: Long,
)

data class ClaimSourceRow(
    val sourceType: String,
    val sourceId: UUID,
    val sourceRevision: Long,
)

/** Read side of drafting: the document, what the user accepted for its application, and the revisions to pin. */
@Repository
class DraftSourceRepository(
    private val jdbc: JdbcTemplate,
) {
    fun document(
        documentId: UUID,
        workspaceId: UUID,
    ): DocumentRow? =
        jdbc
            .query(
                """
                select d.id, d.type, d.language, d.application_id, a.snapshot_id, p.company, p.role_title,
                       (select v.id from document_versions v where v.document_id = d.id order by v.created_at desc, v.id desc limit 1) as latest_version_id
                from documents d
                join applications a on a.id = d.application_id
                join job_posting_snapshots s on s.id = a.snapshot_id
                join job_postings p on p.id = s.posting_id
                where d.id = ? and d.workspace_id = ? and d.deleted_at is null
                """.trimIndent(),
                { rs, _ ->
                    DocumentRow(
                        id = rs.getObject("id", UUID::class.java),
                        type = rs.getString("type"),
                        language = rs.getString("language"),
                        applicationId = rs.getObject("application_id", UUID::class.java),
                        snapshotId = rs.getObject("snapshot_id", UUID::class.java),
                        company = rs.getString("company"),
                        roleTitle = rs.getString("role_title"),
                        latestVersionId = rs.getObject("latest_version_id", UUID::class.java),
                    )
                },
                documentId,
                workspaceId,
            ).firstOrNull()

    /** Claim → requirements the user ACCEPTED it for (F05 decisions). */
    fun acceptedClaims(applicationId: UUID): Map<UUID, Set<UUID>> =
        jdbc
            .query(
                "select claim_id, requirement_id from requirement_matches where application_id = ? and user_decision = 'ACCEPTED'",
                { rs, _ ->
                    rs.getObject("claim_id", UUID::class.java) to
                        rs.getObject("requirement_id", UUID::class.java)
                },
                applicationId,
            ).groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    fun claimRevisions(claimIds: Collection<UUID>): Map<UUID, Long> =
        if (claimIds.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .query(
                    "select id, revision from claims where id in (${claimIds.joinToString(",") { "?" }})",
                    { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getLong("revision") },
                    *claimIds.toTypedArray(),
                ).toMap()
        }

    /** Supporting evidence only; refuting evidence is never offered as something to cite. */
    fun supportingEvidence(claimIds: Collection<UUID>): Map<UUID, List<EvidenceRow>> =
        if (claimIds.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .query(
                    """
                    select ce.claim_id, e.id, e.title, e.revision
                    from claim_evidence ce join evidence e on e.id = ce.evidence_id
                    where ce.claim_id in (${claimIds.joinToString(
                        ",",
                    ) { "?" }}) and ce.relation <> 'REFUTES' and e.deleted_at is null
                    """.trimIndent(),
                    { rs, _ ->
                        rs.getObject("claim_id", UUID::class.java) to
                            EvidenceRow(
                                rs.getObject("id", UUID::class.java),
                                rs.getString("title"),
                                rs.getLong("revision"),
                            )
                    },
                    *claimIds.toTypedArray(),
                ).groupBy({ it.first }, { it.second })
        }

    fun claimSources(claimIds: Collection<UUID>): Map<UUID, List<ClaimSourceRow>> =
        if (claimIds.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .query(
                    """
                    select claim_id, source_type, source_id, source_revision from claim_sources
                    where claim_id in (${placeholders(claimIds)})
                    """.trimIndent(),
                    { rs, _ ->
                        rs.getObject("claim_id", UUID::class.java) to
                            ClaimSourceRow(
                                rs.getString("source_type"),
                                rs.getObject("source_id", UUID::class.java),
                                rs.getLong("source_revision"),
                            )
                    },
                    *claimIds.toTypedArray(),
                ).groupBy({ it.first }, { it.second })
        }

    private fun placeholders(ids: Collection<UUID>) = ids.joinToString(",") { "?" }

    fun requirementVersions(requirementIds: Collection<UUID>): Map<UUID, Long> =
        if (requirementIds.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .query(
                    "select id, version from requirements where id in (${requirementIds.joinToString(",") { "?" }})",
                    { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getLong("version") },
                    *requirementIds.toTypedArray(),
                ).toMap()
        }
}
