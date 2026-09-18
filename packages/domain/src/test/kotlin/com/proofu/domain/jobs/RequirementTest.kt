package com.proofu.domain.jobs

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.RequirementId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class RequirementTest {
    private val snapshotId = JobPostingSnapshotId(Fixtures.uuid(100))
    private val snapshot =
        JobPostingSnapshot.capture(
            snapshotId,
            JobPostingId(Fixtures.uuid(101)),
            SnapshotSource.MANUAL_TEXT,
            "Kotlin 5+ years",
            Instant.EPOCH,
        )

    @Test
    fun `manual requirements are approved as written`() {
        val r =
            Requirement.manual(
                RequirementId(Fixtures.uuid(102)),
                snapshotId,
                RequirementCategory.REQUIRED,
                "  Kotlin  ",
                null,
                Instant.EPOCH,
            )
        assertThat(r.status).isEqualTo(RequirementStatus.APPROVED)
        assertThat(r.origin).isEqualTo(RequirementOrigin.USER)
        assertThat(r.text).isEqualTo("Kotlin")
        assertThat(r.usableForMatching).isTrue()
    }

    @Test
    fun `extracted requirements are drafts until approved`() {
        val r =
            Requirement.extracted(
                RequirementId(Fixtures.uuid(103)),
                snapshotId,
                RequirementCategory.SKILL,
                "Kotlin",
                Confidence(0.7),
                SourceSpan(0, 6),
            )
        assertThat(r.status).isEqualTo(RequirementStatus.DRAFT)
        assertThat(r.usableForMatching).isFalse()
        assertThat(r.approve(Instant.EPOCH).usableForMatching).isTrue()
        assertThat(r.approve(Instant.EPOCH).reject().approvedAt).isNull()
    }

    @Test
    fun `approvedAt and status must agree`() {
        assertThatThrownBy {
            Requirement(
                RequirementId(Fixtures.uuid(104)),
                snapshotId,
                RequirementCategory.SKILL,
                "x",
                Confidence.FULL,
                RequirementOrigin.USER,
                status = RequirementStatus.APPROVED,
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `source spans must lie inside the captured text`() {
        assertThat(snapshot.excerpt(SourceSpan(0, 6))).isEqualTo("Kotlin")
        assertThatThrownBy { snapshot.excerpt(SourceSpan(10, 40)) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { SourceSpan(3, 3) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
