package com.proofu.domain.jobs

import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.JobPostingSnapshotId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class JobPostingSnapshotTest {
    private val postingId = JobPostingId(Fixtures.uuid(80))

    private fun capture(text: String) =
        JobPostingSnapshot.capture(
            JobPostingSnapshotId(Fixtures.uuid(81)),
            postingId,
            SnapshotSource.MANUAL_TEXT,
            text,
            Instant.EPOCH,
        )

    @Test
    fun `whitespace-only differences share a hash, real changes do not`() {
        val a = capture("Backend Engineer\r\n- Kotlin  \n\n")
        val b = capture("Backend Engineer\n- Kotlin")
        val c = capture("Backend Engineer\n- Kotlin\n- 5+ years")
        assertThat(a.contentHash).isEqualTo(b.contentHash).matches("[0-9a-f]{64}")
        assertThat(a.rawText).isEqualTo("Backend Engineer\n- Kotlin")
        assertThat(c.contentHash).isNotEqualTo(a.contentHash)
    }

    @Test
    fun `blank text cannot be captured`() {
        assertThatThrownBy { capture("   \n  ") }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `hash must be lowercase sha256 hex`() {
        assertThatThrownBy {
            JobPostingSnapshot(
                JobPostingSnapshotId(Fixtures.uuid(82)),
                postingId,
                SnapshotSource.MANUAL_TEXT,
                "text",
                "ABC",
                Instant.EPOCH,
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `posting needs company, role and an absolute canonical url`() {
        assertThatThrownBy {
            JobPosting(postingId, Fixtures.workspaceA, " ", "PM")
        }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { JobPosting(postingId, Fixtures.workspaceA, "ABC", "PM", canonicalUrl = "abc.com/jobs/1") }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThat(
            JobPosting(postingId, Fixtures.workspaceA, "ABC", "PM", canonicalUrl = "https://abc.com/jobs/1").company,
        ).isEqualTo("ABC")
    }
}
