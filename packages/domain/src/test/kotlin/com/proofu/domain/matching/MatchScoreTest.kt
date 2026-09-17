package com.proofu.domain.matching

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MatchScoreTest {
    private fun features(all: Double) = MatchFeatures(all, all, all, all, all, all)

    @Test
    fun `weights sum to one`() {
        val sum =
            MatchScore.W_REQUIREMENT_COVERAGE + MatchScore.W_EVIDENCE_STRENGTH + MatchScore.W_RECENCY +
                MatchScore.W_COMPLEXITY + MatchScore.W_IMPACT + MatchScore.W_SEMANTIC_SIMILARITY
        assertThat(sum).isCloseTo(
            1.0,
            org.assertj.core.data.Offset
                .offset(1e-9),
        )
    }

    @Test
    fun `perfect features score 100 and HIGH`() {
        val score = MatchScore.compute(features(1.0))
        assertThat(score.value).isEqualTo(100)
        assertThat(score.band).isEqualTo(ScoreBand.HIGH)
    }

    @Test
    fun `zero features score 0 and LOW`() {
        val score = MatchScore.compute(features(0.0))
        assertThat(score.value).isZero()
        assertThat(score.band).isEqualTo(ScoreBand.LOW)
    }

    @Test
    fun `requirement coverage carries the most weight`() {
        val onlyCoverage = MatchFeatures(1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val onlySemantic = MatchFeatures(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        assertThat(MatchScore.compute(onlyCoverage).value).isEqualTo(30)
        assertThat(MatchScore.compute(onlySemantic).value).isEqualTo(10)
    }

    @Test
    fun `bands follow documented thresholds`() {
        assertThat(ScoreBand.of(39)).isEqualTo(ScoreBand.LOW)
        assertThat(ScoreBand.of(40)).isEqualTo(ScoreBand.MEDIUM)
        assertThat(ScoreBand.of(69)).isEqualTo(ScoreBand.MEDIUM)
        assertThat(ScoreBand.of(70)).isEqualTo(ScoreBand.HIGH)
    }

    @Test
    fun `features outside the unit interval are rejected`() {
        assertThatThrownBy { MatchFeatures(1.2, 0.0, 0.0, 0.0, 0.0, 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
