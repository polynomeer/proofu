package com.proofu.worker.matching

import com.proofu.ai.matching.CandidateToExplain
import com.proofu.ai.matching.MatchExplainer
import com.proofu.ai.matching.RequirementToExplain
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.matching.MatchFeatureCalculator
import com.proofu.domain.matching.MatchScore
import com.proofu.worker.jobs.AiFailures
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * F05: for every approved requirement of the application's snapshot, score the workspace's
 * claims deterministically, ask the model to explain the strongest candidates, and store the
 * result. User decisions on existing rows are preserved across runs (design §8.3).
 */
@Component
class ApplicationMatchJobHandler(
    private val candidates: CandidateRepository,
    private val explainer: MatchExplainer,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val ids: IdGenerator,
) : JobHandler {
    private val log = LoggerFactory.getLogger(ApplicationMatchJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val applicationId = UUID.fromString(mapper.readTree(job.payload).get("applicationId").asString())
        val snapshotId =
            jdbc
                .query(
                    "select snapshot_id from applications where id = ? and workspace_id = ? and deleted_at is null",
                    { rs, _ -> rs.getObject("snapshot_id", UUID::class.java) },
                    applicationId,
                    job.workspaceId,
                ).firstOrNull()
                ?: throw JobFailure("APPLICATION_NOT_FOUND", "application $applicationId", retryable = false)

        val requirements = candidates.approvedRequirements(snapshotId)
        val all = candidates.candidates(job.workspaceId)
        val (eligible, sensitive) = all.partition { it.sensitivity.allowedInAiContextByDefault }
        val today = LocalDate.now(clock)

        // Deterministic scoring: top candidates per requirement, weakest noise dropped.
        val scored =
            requirements.associate { r ->
                r.id to
                    eligible
                        .map { c -> c to MatchScore.compute(MatchFeatureCalculator.compute(r.text, c.profile, today)) }
                        .filter { (_, s) -> s.value >= MIN_SCORE }
                        .sortedByDescending { (_, s) -> s.value }
                        .take(TOP_K)
            }

        // Explanations only for the strongest few; everything else keeps a null reason.
        val toExplain =
            requirements.mapNotNull { r ->
                val top = scored.getValue(r.id).take(EXPLAIN_K)
                if (top.isEmpty()) {
                    null
                } else {
                    RequirementToExplain(
                        r.id,
                        r.category,
                        r.text,
                        top.map { (c, s) ->
                            CandidateToExplain(
                                c.claimId,
                                c.profile.claimText,
                                c.profile.sourceText,
                                c.evidenceTitles,
                                c.sensitivity,
                                s.value,
                            )
                        },
                    )
                }
            }
        val explained =
            if (toExplain.isEmpty()) {
                null
            } else {
                AiFailures.guard {
                    explainer.explain(WorkspaceId(job.workspaceId), toExplain, job.id)
                }
            }
        val reasons = explained?.explanations?.associateBy { it.requirementId to it.claimId } ?: emptyMap()

        val stored =
            tx.execute {
                val keep = mutableListOf<Pair<UUID, UUID>>()
                scored.forEach { (requirementId, ranked) ->
                    ranked.forEachIndexed { index, (c, score) ->
                        keep += requirementId to c.claimId
                        val reason = reasons[requirementId to c.claimId]
                        jdbc.update(
                            """
                            insert into requirement_matches
                              (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring,
                               reason, matched_requirement_phrase, matched_evidence_phrase, reason_execution_id, run_job_id)
                            values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                            on conflict (application_id, requirement_id, claim_id) do update set
                              rank = excluded.rank, score = excluded.score, band = excluded.band, features = excluded.features,
                              claim_status_at_scoring = excluded.claim_status_at_scoring,
                              reason = coalesce(excluded.reason, requirement_matches.reason),
                              matched_requirement_phrase = coalesce(excluded.matched_requirement_phrase, requirement_matches.matched_requirement_phrase),
                              matched_evidence_phrase = coalesce(excluded.matched_evidence_phrase, requirement_matches.matched_evidence_phrase),
                              reason_execution_id = coalesce(excluded.reason_execution_id, requirement_matches.reason_execution_id),
                              run_job_id = excluded.run_job_id
                            """.trimIndent(),
                            ids.next(),
                            applicationId,
                            requirementId,
                            c.claimId,
                            index + 1,
                            score.value,
                            score.band.name,
                            mapper.writeValueAsString(score.features),
                            c.profile.claimStatus.name,
                            reason?.reason,
                            reason?.matchedRequirementPhrase,
                            reason?.matchedEvidencePhrase,
                            if (reason != null) explained?.executionId else null,
                            job.id,
                        )
                    }
                }
                // Rows no longer produced go away unless the user decided on them.
                val existing =
                    jdbc.query(
                        "select id, requirement_id, claim_id from requirement_matches where application_id = ? and user_decision is null",
                        {
                            rs,
                            _,
                            ->
                            Triple(
                                rs.getObject("id", UUID::class.java),
                                rs.getObject("requirement_id", UUID::class.java),
                                rs.getObject("claim_id", UUID::class.java),
                            )
                        },
                        applicationId,
                    )
                val stale = existing.filter { (_, r, c) -> (r to c) !in keep }
                stale.forEach { (id, _, _) -> jdbc.update("delete from requirement_matches where id = ?", id) }
                keep.size
            }

        log.info(
            "application.match application={} requirements={} candidates={} sensitiveSkipped={} stored={} explained={} cost={}µ$",
            applicationId,
            requirements.size,
            eligible.size,
            sensitive.size,
            stored,
            reasons.size,
            explained?.costMicros ?: 0,
        )
        return mapper.writeValueAsString(
            mapOf(
                "applicationId" to applicationId.toString(),
                "requirements" to requirements.size,
                "candidates" to eligible.size,
                "sensitiveSkipped" to sensitive.size,
                "stored" to stored,
                "explained" to reasons.size,
                "dropped" to (explained?.dropped ?: emptyList()),
                "costMicros" to (explained?.costMicros ?: 0),
            ),
        )
    }

    companion object {
        /** Must match apps/api JobTypes.APPLICATION_MATCH. */
        const val TYPE = "application.match"
        const val TOP_K = 5
        const val EXPLAIN_K = 3
        const val MIN_SCORE = 15
    }
}
