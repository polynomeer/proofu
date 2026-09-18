package com.proofu.domain.matching

import com.proofu.domain.evidence.ClaimStatus
import com.proofu.domain.evidence.VerificationStatus
import com.proofu.domain.jobs.RequirementCategory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything the scorer needs to know about one claim, gathered by the caller from its sources. */
data class CandidateProfile(
    val claimText: String,
    /** Text of the record the claim is about (achievement action/outcome, project summary, entry title). */
    val sourceText: String,
    val claimStatus: ClaimStatus,
    val evidenceVerifications: List<VerificationStatus>,
    /** Most recent date of the underlying record (end date, or start date when ongoing); null when unknown. */
    val latestDate: LocalDate?,
    val hasMetric: Boolean,
    val sourceLevel: SourceLevel,
)

/** Where in the career hierarchy the claim's source sits; deeper records tend to carry more specific evidence. */
enum class SourceLevel {
    CAREER_ENTRY,
    PROJECT,
    ACHIEVEMENT,
}

/** Requirement-level verdict for REQUIRED items (matching §10.3). Other categories only rank. */
enum class RequirementAssessment {
    MET,
    PARTIALLY_MET,
    UNVERIFIED,
    UNMET,
}

/** The user's call on a recommendation; AI re-runs never overwrite it (design §8.3). */
enum class MatchDecision {
    ACCEPTED,
    REJECTED,
}

/**
 * Deterministic feature extraction (matching §10.1 step 4). Semantic similarity is 0 until an
 * embedding index exists, so the maximum reachable score is 90 — the UI shows the number, not
 * a percentage of "fit".
 */
object MatchFeatureCalculator {
    fun compute(
        requirementText: String,
        candidate: CandidateProfile,
        today: LocalDate,
    ): MatchFeatures =
        MatchFeatures(
            requirementCoverage = coverage(requirementText, candidate.claimText + " " + candidate.sourceText),
            evidenceStrength = evidenceStrength(candidate),
            recency = recency(candidate.latestDate, today),
            complexity = complexity(candidate),
            impact = if (candidate.hasMetric) 0.8 else 0.4,
            semanticSimilarity = 0.0,
        )

    /** Share of the requirement's content tokens that appear in the candidate text (order-free). */
    fun coverage(
        requirement: String,
        candidate: String,
    ): Double {
        val need = tokens(requirement)
        if (need.isEmpty()) return 0.0
        val have = tokens(candidate)
        // Prefix matching absorbs Korean particles (기획 ~ 기획을) and English inflection (lead ~ leading).
        val hits =
            need.count { n ->
                have.any { h -> h == n || h.startsWith(n) || (h.length >= 3 && n.startsWith(h)) }
            }
        return hits.toDouble() / need.size
    }

    /** Verified evidence counts fully, unverified half, expired barely; two strong links saturate. */
    fun evidenceStrength(candidate: CandidateProfile): Double {
        if (candidate.claimStatus == ClaimStatus.CONTESTED) return 0.1
        val sum =
            candidate.evidenceVerifications.sumOf {
                when (it) {
                    VerificationStatus.USER_VERIFIED, VerificationStatus.EXTERNALLY_VERIFIED -> 1.0
                    VerificationStatus.UNVERIFIED -> 0.5
                    VerificationStatus.EXPIRED -> 0.2
                }
            }
        return (sum / 2.0).coerceIn(0.0, 1.0)
    }

    fun recency(
        latest: LocalDate?,
        today: LocalDate,
    ): Double {
        if (latest == null) return 0.5
        val months = ChronoUnit.MONTHS.between(latest, today)
        return when {
            months <= 12 -> 1.0
            months <= 36 -> 0.7
            months <= 60 -> 0.4
            else -> 0.2
        }
    }

    private fun complexity(c: CandidateProfile): Double =
        when (c.sourceLevel) {
            SourceLevel.ACHIEVEMENT -> if (c.hasMetric) 0.9 else 0.7
            SourceLevel.PROJECT -> 0.6
            SourceLevel.CAREER_ENTRY -> 0.4
        }

    /** Lowercased alphanumeric/Hangul tokens of length ≥ 2, minus very common words. */
    fun tokens(text: String): Set<String> =
        TOKEN
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 2 && it !in STOP }
            .toSet()

    private val TOKEN = Regex("[\\p{IsHangul}\\p{IsLatin}\\p{IsDigit}+#.]+")
    private val STOP =
        setOf(
            "이상",
            "경험",
            "경력",
            "가능",
            "가능자",
            "능력",
            "및",
            "또는",
            "관련",
            "업무",
            "우대",
            "필수",
            "있는",
            "분",
            "and",
            "or",
            "with",
            "the",
            "of",
            "in",
            "for",
            "to",
            "experience",
            "years",
            "year",
            "strong",
        )
}

/**
 * Requirement verdict from the best candidate (matching §10.3). Only REQUIRED items get a verdict
 * that the UI must show as a gap regardless of score; other categories rank only.
 */
object RequirementAssessor {
    fun assess(
        category: RequirementCategory,
        best: MatchScore?,
        bestClaimStatus: ClaimStatus?,
    ): RequirementAssessment? {
        if (category != RequirementCategory.REQUIRED) return null
        if (best == null) return RequirementAssessment.UNMET
        return when {
            bestClaimStatus == ClaimStatus.UNSUPPORTED -> RequirementAssessment.UNVERIFIED
            best.band == ScoreBand.HIGH -> RequirementAssessment.MET
            best.band == ScoreBand.MEDIUM -> RequirementAssessment.PARTIALLY_MET
            else -> RequirementAssessment.UNMET
        }
    }
}
