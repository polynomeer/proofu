package com.proofu.api.claim

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Revision
import com.proofu.domain.evidence.ClaimEvidenceLink
import com.proofu.domain.evidence.ClaimSource
import com.proofu.domain.evidence.ClaimSourceType
import com.proofu.domain.evidence.EvidenceRelation
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Loads claim_sources and claim_evidence for a set of claims and derives each claim's status
 * with the domain rule. Links to deleted evidence are ignored.
 */
@Component
class ClaimAssembler(
    private val jdbc: JdbcTemplate,
) {
    fun sourcesOf(claimId: UUID): List<ClaimSource> = sourcesFor(listOf(claimId))[claimId] ?: emptyList()

    fun assemble(claims: List<ClaimEntity>): List<ClaimResponse> {
        if (claims.isEmpty()) return emptyList()
        val ids = claims.map { it.id }
        val sources = sourcesFor(ids)
        val links = linksFor(ids)
        return claims.map { c ->
            val claimLinks = links[c.id] ?: emptyList()
            val status =
                ClaimEvidenceLink.statusOf(
                    claimLinks.map {
                        ClaimEvidenceLink(
                            ClaimId(c.id),
                            EvidenceId(it.evidenceId),
                            it.relation,
                            Confidence(it.confidence),
                            it.scope,
                        )
                    },
                )
            ClaimResponse(
                id = c.id,
                text = c.text,
                type = c.type,
                sensitivity = c.sensitivity,
                status = status,
                sources = (sources[c.id] ?: emptyList()).map { ClaimSourceResponse(it.type, it.id, it.revision.value) },
                links = claimLinks,
                revision = c.revision,
                createdAt = checkNotNull(c.createdAt),
                updatedAt = checkNotNull(c.updatedAt),
            )
        }
    }

    private fun sourcesFor(claimIds: List<UUID>): Map<UUID, List<ClaimSource>> {
        val placeholders = claimIds.joinToString(",") { "?" }
        return jdbc
            .query(
                "select claim_id, source_type, source_id, source_revision from claim_sources where claim_id in ($placeholders)",
                { rs, _ ->
                    rs.getObject("claim_id", UUID::class.java) to
                        ClaimSource(
                            ClaimSourceType.valueOf(rs.getString("source_type")),
                            rs.getObject("source_id", UUID::class.java),
                            Revision(rs.getLong("source_revision")),
                        )
                },
                *claimIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }

    private fun linksFor(claimIds: List<UUID>): Map<UUID, List<ClaimEvidenceLinkResponse>> {
        val placeholders = claimIds.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select ce.claim_id, ce.evidence_id, ce.relation, ce.confidence, ce.scope,
                       e.title, e.type, e.verification
                from claim_evidence ce join evidence e on e.id = ce.evidence_id
                where ce.claim_id in ($placeholders) and e.deleted_at is null
                order by ce.created_at
                """.trimIndent(),
                { rs, _ ->
                    rs.getObject("claim_id", UUID::class.java) to
                        ClaimEvidenceLinkResponse(
                            evidenceId = rs.getObject("evidence_id", UUID::class.java),
                            evidenceTitle = rs.getString("title"),
                            evidenceType = EvidenceType.valueOf(rs.getString("type")),
                            evidenceVerification = VerificationStatus.valueOf(rs.getString("verification")),
                            relation = EvidenceRelation.valueOf(rs.getString("relation")),
                            confidence = rs.getBigDecimal("confidence").toDouble(),
                            scope = rs.getString("scope"),
                        )
                },
                *claimIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }
}
