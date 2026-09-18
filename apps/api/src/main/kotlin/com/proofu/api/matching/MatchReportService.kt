package com.proofu.api.matching

import com.proofu.api.application.ApplicationService
import com.proofu.api.claim.ClaimAssembler
import com.proofu.api.claim.ClaimRepository
import com.proofu.api.claim.ClaimResponse
import com.proofu.api.claim.ClaimSourceResponse
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.requirement.RequirementResponse
import com.proofu.api.requirement.RequirementService
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.domain.evidence.ClaimStatus
import com.proofu.domain.evidence.EvidenceRelation
import com.proofu.domain.evidence.VerificationStatus
import com.proofu.domain.jobs.RequirementCategory
import com.proofu.domain.jobs.RequirementStatus
import com.proofu.domain.matching.MatchDecision
import com.proofu.domain.matching.MatchFeatures
import com.proofu.domain.matching.MatchScore
import com.proofu.domain.matching.RequirementAssessment
import com.proofu.domain.matching.RequirementAssessor
import com.proofu.domain.matching.ScoreBand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class MatchEvidence(
    val id: UUID,
    val title: String,
    val verification: VerificationStatus,
    val relation: EvidenceRelation,
)

data class RequirementMatchResponse(
    val id: UUID,
    val claimId: UUID,
    val claimText: String,
    val claimStatus: ClaimStatus,
    val sources: List<ClaimSourceResponse>,
    val evidence: List<MatchEvidence>,
    val score: Int,
    val band: ScoreBand,
    val features: MatchFeatures,
    val reason: String?,
    val matchedRequirementPhrase: String?,
    val matchedEvidencePhrase: String?,
    val userDecision: MatchDecision?,
)

data class RequirementMatchGroup(
    val requirement: RequirementResponse,
    val assessment: RequirementAssessment?,
    val candidates: List<RequirementMatchResponse>,
)

data class MatchReport(
    val applicationId: UUID,
    val hasRun: Boolean,
    val lastRunAt: Instant?,
    val groups: List<RequirementMatchGroup>,
    val gaps: List<UUID>,
)

/** Assembles the J02 report from requirement_matches, live claims and approved requirements. */
@Service
class MatchReportService(
    private val applications: ApplicationService,
    private val requirements: RequirementService,
    private val claims: ClaimRepository,
    private val assembler: ClaimAssembler,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun report(
        workspace: WorkspaceContext,
        applicationId: UUID,
    ): MatchReport {
        val application = applications.get(workspace, applicationId)
        val approved =
            requirements.listForSnapshot(workspace, application.snapshotId).items.filter {
                it.status ==
                    RequirementStatus.APPROVED
            }
        val rows = rows(applicationId)
        val claimById =
            assembler
                .assemble(
                    claims
                        .findAllById(
                            rows
                                .map {
                                    it.claimId
                                }.distinct(),
                        ).filter { it.deletedAt == null },
                ).associateBy { it.id }

        val groups =
            approved
                .map { requirement ->
                    val candidates =
                        rows
                            .filter { it.requirementId == requirement.id && it.claimId in claimById }
                            .sortedBy { it.rank }
                            .map { it.toResponse(claimById.getValue(it.claimId)) }
                    val best = candidates.firstOrNull()
                    val assessment =
                        RequirementAssessor.assess(
                            requirement.category,
                            best?.let { MatchScore(it.score, it.band, it.features) },
                            best?.claimStatus,
                        )
                    RequirementMatchGroup(requirement, assessment, candidates)
                }.sortedWith(
                    compareBy<RequirementMatchGroup> { it.requirement.category != RequirementCategory.REQUIRED }
                        .thenByDescending { it.candidates.firstOrNull()?.score ?: -1 },
                )
        val lastRun =
            jdbc
                .query(
                    "select max(finished_at) from jobs where workspace_id = ? and type = 'application.match' and status = 'SUCCEEDED' and payload ->> 'applicationId' = ?",
                    { rs, _ -> rs.getTimestamp(1)?.toInstant() },
                    workspace.workspaceId.value,
                    applicationId.toString(),
                ).firstOrNull()
        return MatchReport(
            applicationId = applicationId,
            hasRun = lastRun != null,
            lastRunAt = lastRun,
            groups = groups,
            gaps =
                groups
                    .filter {
                        it.assessment == RequirementAssessment.UNMET ||
                            it.assessment == RequirementAssessment.UNVERIFIED
                    }.map { it.requirement.id },
        )
    }

    @Transactional
    fun decide(
        workspace: WorkspaceContext,
        matchId: UUID,
        decision: MatchDecision?,
    ): RequirementMatchResponse {
        val updated =
            jdbc.update(
                """
                update requirement_matches m set user_decision = ?
                from applications a
                where m.id = ? and a.id = m.application_id and a.workspace_id = ? and a.deleted_at is null
                """.trimIndent(),
                decision?.name,
                matchId,
                workspace.workspaceId.value,
            )
        if (updated == 0) throw ResourceNotFoundException("requirement_match", matchId)
        val row = rows(matchId = matchId).single()
        val claim = assembler.assemble(listOfNotNull(claims.findById(row.claimId).orElse(null))).single()
        return row.toResponse(claim)
    }

    private data class Row(
        val id: UUID,
        val applicationId: UUID,
        val requirementId: UUID,
        val claimId: UUID,
        val rank: Int,
        val score: Int,
        val band: ScoreBand,
        val features: MatchFeatures,
        val reason: String?,
        val matchedRequirementPhrase: String?,
        val matchedEvidencePhrase: String?,
        val userDecision: MatchDecision?,
    ) {
        fun toResponse(claim: ClaimResponse) =
            RequirementMatchResponse(
                id = id,
                claimId = claimId,
                claimText = claim.text,
                claimStatus = claim.status,
                sources = claim.sources,
                evidence =
                    claim.links.map {
                        MatchEvidence(
                            it.evidenceId,
                            it.evidenceTitle,
                            it.evidenceVerification,
                            it.relation,
                        )
                    },
                score = score,
                band = band,
                features = features,
                reason = reason,
                matchedRequirementPhrase = matchedRequirementPhrase,
                matchedEvidencePhrase = matchedEvidencePhrase,
                userDecision = userDecision,
            )
    }

    private fun rows(
        applicationId: UUID? = null,
        matchId: UUID? = null,
    ): List<Row> =
        jdbc.query(
            """
            select id, application_id, requirement_id, claim_id, rank, score, band, features::text as features, reason,
                   matched_requirement_phrase, matched_evidence_phrase, user_decision
            from requirement_matches
            where (cast(? as uuid) is null or application_id = cast(? as uuid))
              and (cast(? as uuid) is null or id = cast(? as uuid))
            order by requirement_id, rank
            """.trimIndent(),
            { rs, _ ->
                Row(
                    id = rs.getObject("id", UUID::class.java),
                    applicationId = rs.getObject("application_id", UUID::class.java),
                    requirementId = rs.getObject("requirement_id", UUID::class.java),
                    claimId = rs.getObject("claim_id", UUID::class.java),
                    rank = rs.getInt("rank"),
                    score = rs.getInt("score"),
                    band = ScoreBand.valueOf(rs.getString("band")),
                    features = mapper.readValue(rs.getString("features"), MatchFeatures::class.java),
                    reason = rs.getString("reason"),
                    matchedRequirementPhrase = rs.getString("matched_requirement_phrase"),
                    matchedEvidencePhrase = rs.getString("matched_evidence_phrase"),
                    userDecision = rs.getString("user_decision")?.let(MatchDecision::valueOf),
                )
            },
            applicationId,
            applicationId,
            matchId,
            matchId,
        )
}
