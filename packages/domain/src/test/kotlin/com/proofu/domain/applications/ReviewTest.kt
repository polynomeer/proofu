package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.ReviewId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReviewTest {
    private fun review(
        hypothesis: String,
        rationale: String? = null,
    ) = Review(
        id = ReviewId(Fixtures.uuid(90)),
        applicationId = ApplicationId(Fixtures.uuid(91)),
        observedFact = "제출 8일 후 불합격 이메일 수신",
        hypothesis = hypothesis,
        rationale = rationale,
    )

    @Test
    fun `fact and hypothesis are both required`() {
        assertThatThrownBy { review(" ") }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `definitive cause language is flagged but not rejected`() {
        assertThat(review("필수 경력 요구에 대한 근거가 약했을 수 있음").definitiveLanguage()).isEmpty()
        assertThat(review("경력 연수 때문에 탈락했다").definitiveLanguage()).containsExactly("때문에 탈락")
        assertThat(review("가설", rationale = "원인은 분명히 포트폴리오 부족").definitiveLanguage())
            .containsExactlyInAnyOrder("분명히", "원인은")
    }

    @Test
    fun `only post-result statuses accept a review`() {
        assertThat(ApplicationStatus.entries.filter { it.acceptsReview })
            .containsExactlyInAnyOrder(
                ApplicationStatus.DOCUMENT_REJECTED,
                ApplicationStatus.NO_RESPONSE,
                ApplicationStatus.REVIEW_PENDING,
                ApplicationStatus.REVIEWED,
            )
    }
}
