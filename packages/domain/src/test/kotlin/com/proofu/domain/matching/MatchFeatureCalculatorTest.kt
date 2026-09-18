package com.proofu.domain.matching

import com.proofu.domain.evidence.ClaimStatus
import com.proofu.domain.evidence.VerificationStatus
import com.proofu.domain.jobs.RequirementCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MatchFeatureCalculatorTest {
    private val today = LocalDate.of(2026, 9, 18)

    private fun candidate(
        claim: String,
        source: String = "",
        status: ClaimStatus = ClaimStatus.SUPPORTED,
        evidence: List<VerificationStatus> = listOf(VerificationStatus.USER_VERIFIED),
        latest: LocalDate? = LocalDate.of(2026, 3, 1),
        metric: Boolean = true,
        level: SourceLevel = SourceLevel.ACHIEVEMENT,
    ) = CandidateProfile(claim, source, status, evidence, latest, metric, level)

    @Test
    fun `coverage counts requirement tokens found in the candidate, ignoring stop words`() {
        assertThat(
            MatchFeatureCalculator.coverage("SaaS 제품 기획 경험", "B2B SaaS 제품 로드맵 기획을 리딩"),
        ).isCloseTo(1.0, within(0.01))
        assertThat(MatchFeatureCalculator.coverage("Kotlin 5년 이상", "Java 백엔드 개발")).isEqualTo(0.0)
        assertThat(MatchFeatureCalculator.coverage("PostgreSQL 운영", "postgresql 성능 튜닝")).isCloseTo(0.5, within(0.01))
    }

    @Test
    fun `evidence strength rewards verified links and punishes contested claims`() {
        assertThat(MatchFeatureCalculator.evidenceStrength(candidate("c", evidence = emptyList()))).isEqualTo(0.0)
        assertThat(
            MatchFeatureCalculator.evidenceStrength(candidate("c", evidence = listOf(VerificationStatus.UNVERIFIED))),
        ).isEqualTo(0.25)
        assertThat(
            MatchFeatureCalculator.evidenceStrength(
                candidate(
                    "c",
                    evidence =
                        listOf(
                            VerificationStatus.USER_VERIFIED,
                            VerificationStatus.EXTERNALLY_VERIFIED,
                            VerificationStatus.USER_VERIFIED,
                        ),
                ),
            ),
        ).isEqualTo(1.0)
        assertThat(
            MatchFeatureCalculator.evidenceStrength(candidate("c", status = ClaimStatus.CONTESTED)),
        ).isEqualTo(0.1)
    }

    @Test
    fun `recency steps down with age and is neutral when unknown`() {
        assertThat(MatchFeatureCalculator.recency(LocalDate.of(2026, 1, 1), today)).isEqualTo(1.0)
        assertThat(MatchFeatureCalculator.recency(LocalDate.of(2024, 1, 1), today)).isEqualTo(0.7)
        assertThat(MatchFeatureCalculator.recency(LocalDate.of(2022, 1, 1), today)).isEqualTo(0.4)
        assertThat(MatchFeatureCalculator.recency(LocalDate.of(2015, 1, 1), today)).isEqualTo(0.2)
        assertThat(MatchFeatureCalculator.recency(null, today)).isEqualTo(0.5)
    }

    @Test
    fun `a strong recent achievement with a metric scores HIGH without semantic similarity`() {
        val features =
            MatchFeatureCalculator.compute(
                "SaaS 제품 기획 경험",
                candidate("SaaS 제품 기획을 주도", "온보딩 재설계로 활성화율 40% 개선"),
                today,
            )
        val score = MatchScore.compute(features)
        assertThat(features.semanticSimilarity).isEqualTo(0.0)
        assertThat(score.value).isBetween(70, 90)
        assertThat(score.band).isEqualTo(ScoreBand.HIGH)
    }

    @Test
    fun `required items get a verdict from the best candidate, other categories do not`() {
        val high = MatchScore.compute(MatchFeatures(1.0, 1.0, 1.0, 0.9, 0.8, 0.0))
        val medium = MatchScore.compute(MatchFeatures(0.5, 0.5, 0.7, 0.6, 0.4, 0.0))
        assertThat(
            RequirementAssessor.assess(RequirementCategory.REQUIRED, null, null),
        ).isEqualTo(RequirementAssessment.UNMET)
        assertThat(
            RequirementAssessor.assess(RequirementCategory.REQUIRED, high, ClaimStatus.SUPPORTED),
        ).isEqualTo(RequirementAssessment.MET)
        assertThat(
            RequirementAssessor.assess(RequirementCategory.REQUIRED, high, ClaimStatus.UNSUPPORTED),
        ).isEqualTo(RequirementAssessment.UNVERIFIED)
        assertThat(
            RequirementAssessor.assess(RequirementCategory.REQUIRED, medium, ClaimStatus.SUPPORTED),
        ).isEqualTo(RequirementAssessment.PARTIALLY_MET)
        assertThat(RequirementAssessor.assess(RequirementCategory.PREFERRED, high, ClaimStatus.SUPPORTED)).isNull()
    }
}
