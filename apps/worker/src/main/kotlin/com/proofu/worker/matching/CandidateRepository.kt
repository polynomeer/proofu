package com.proofu.worker.matching

import com.proofu.domain.common.Sensitivity
import com.proofu.domain.evidence.ClaimStatus
import com.proofu.domain.evidence.EvidenceRelation
import com.proofu.domain.evidence.VerificationStatus
import com.proofu.domain.matching.CandidateProfile
import com.proofu.domain.matching.SourceLevel
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

data class ApprovedRequirement(
    val id: UUID,
    val category: String,
    val text: String,
)

data class Candidate(
    val claimId: UUID,
    val sensitivity: Sensitivity,
    val profile: CandidateProfile,
    val evidenceTitles: List<String>,
)

/** Read side of matching: approved requirements and every live claim with what the scorer needs. */
@Repository
class CandidateRepository(
    private val jdbc: JdbcTemplate,
) {
    fun approvedRequirements(snapshotId: UUID): List<ApprovedRequirement> =
        jdbc.query(
            """
            select id, category, text from requirements
            where snapshot_id = ? and status = 'APPROVED' and deleted_at is null
            order by sort_order, created_at
            """.trimIndent(),
            {
                rs,
                _,
                ->
                ApprovedRequirement(
                    rs.getObject("id", UUID::class.java),
                    rs.getString("category"),
                    rs.getString("text"),
                )
            },
            snapshotId,
        )

    /**
     * One candidate per live claim in the workspace. Source text and dates come from the first
     * claim source (achievement → project → career entry precedence); evidence links carry their
     * verification so the domain can weigh them.
     */
    fun candidates(workspaceId: UUID): List<Candidate> {
        val claims =
            jdbc.query(
                """
                select c.id, c.text, c.sensitivity,
                       src.source_type, src.source_text, src.latest_date, src.has_metric
                from claims c
                left join lateral (
                    select cs.source_type,
                           case cs.source_type
                             when 'ACHIEVEMENT' then (select a.action || ' ' || a.outcome from achievements a where a.id = cs.source_id)
                             when 'PROJECT' then (select p.name || ' ' || p.role || ' ' || p.summary from projects p where p.id = cs.source_id)
                             else (select e.title || ' ' || coalesce(e.organization, '') || ' ' || coalesce(e.description, '') from career_entries e where e.id = cs.source_id)
                           end as source_text,
                           case cs.source_type
                             when 'ACHIEVEMENT' then (select coalesce(p.end_date, p.start_date) from achievements a join projects p on p.id = a.project_id where a.id = cs.source_id)
                             when 'PROJECT' then (select coalesce(p.end_date, p.start_date) from projects p where p.id = cs.source_id)
                             else (select coalesce(e.end_date, e.start_date) from career_entries e where e.id = cs.source_id)
                           end as latest_date,
                           case cs.source_type
                             when 'ACHIEVEMENT' then (select a.metric_value is not null from achievements a where a.id = cs.source_id)
                             else false
                           end as has_metric
                    from claim_sources cs
                    where cs.claim_id = c.id
                    order by case cs.source_type when 'ACHIEVEMENT' then 0 when 'PROJECT' then 1 else 2 end
                    limit 1
                ) src on true
                where c.workspace_id = ? and c.deleted_at is null
                """.trimIndent(),
                { rs, _ ->
                    ClaimRow(
                        id = rs.getObject("id", UUID::class.java),
                        text = rs.getString("text"),
                        sensitivity = Sensitivity.valueOf(rs.getString("sensitivity")),
                        sourceType = rs.getString("source_type"),
                        sourceText = rs.getString("source_text") ?: "",
                        latestDate = rs.getDate("latest_date")?.toLocalDate(),
                        hasMetric = rs.getBoolean("has_metric"),
                    )
                },
                workspaceId,
            )
        if (claims.isEmpty()) return emptyList()
        val placeholders = claims.joinToString(",") { "?" }
        val links =
            jdbc
                .query(
                    """
                    select ce.claim_id, ce.relation, e.verification, e.title
                    from claim_evidence ce join evidence e on e.id = ce.evidence_id
                    where ce.claim_id in ($placeholders) and e.deleted_at is null
                    """.trimIndent(),
                    { rs, _ ->
                        rs.getObject("claim_id", UUID::class.java) to
                            LinkRow(
                                EvidenceRelation.valueOf(rs.getString("relation")),
                                VerificationStatus.valueOf(rs.getString("verification")),
                                rs.getString("title"),
                            )
                    },
                    *claims.map { it.id }.toTypedArray(),
                ).groupBy({ it.first }, { it.second })

        return claims.map { c ->
            val claimLinks = links[c.id] ?: emptyList()
            val status =
                when {
                    claimLinks.isEmpty() -> ClaimStatus.UNSUPPORTED
                    claimLinks.any { it.relation == EvidenceRelation.REFUTES } -> ClaimStatus.CONTESTED
                    else -> ClaimStatus.SUPPORTED
                }
            Candidate(
                claimId = c.id,
                sensitivity = c.sensitivity,
                profile =
                    CandidateProfile(
                        claimText = c.text,
                        sourceText = c.sourceText,
                        claimStatus = status,
                        evidenceVerifications = claimLinks.map { it.verification },
                        latestDate = c.latestDate,
                        hasMetric = c.hasMetric,
                        sourceLevel =
                            when (c.sourceType) {
                                "ACHIEVEMENT" -> SourceLevel.ACHIEVEMENT
                                "PROJECT" -> SourceLevel.PROJECT
                                else -> SourceLevel.CAREER_ENTRY
                            },
                    ),
                evidenceTitles = claimLinks.map { it.title },
            )
        }
    }

    private data class ClaimRow(
        val id: UUID,
        val text: String,
        val sensitivity: Sensitivity,
        val sourceType: String?,
        val sourceText: String,
        val latestDate: LocalDate?,
        val hasMetric: Boolean,
    )

    private data class LinkRow(
        val relation: EvidenceRelation,
        val verification: VerificationStatus,
        val title: String,
    )
}
