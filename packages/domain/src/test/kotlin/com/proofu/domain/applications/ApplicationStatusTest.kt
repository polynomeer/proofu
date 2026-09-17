package com.proofu.domain.applications

import com.proofu.domain.applications.ApplicationStatus.DOCUMENT_PASSED
import com.proofu.domain.applications.ApplicationStatus.HANDED_OFF_TO_ITERVIEW
import com.proofu.domain.applications.ApplicationStatus.HANDOFF_READY
import com.proofu.domain.applications.ApplicationStatus.INTERESTED
import com.proofu.domain.applications.ApplicationStatus.PREPARING
import com.proofu.domain.applications.ApplicationStatus.REVIEWED
import com.proofu.domain.applications.ApplicationStatus.WITHDRAWN
import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.InvalidStatusTransition
import com.proofu.domain.common.JobPostingSnapshotId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

class ApplicationStatusTest {
    @ParameterizedTest
    @CsvSource(
        "INTERESTED, PREPARING",
        "PREPARING, SUBMITTED",
        "SUBMITTED, DOCUMENT_PASSED",
        "SUBMITTED, DOCUMENT_REJECTED",
        "SUBMITTED, NO_RESPONSE",
        "SUBMITTED, WITHDRAWN",
        "DOCUMENT_PASSED, HANDOFF_READY",
        "HANDOFF_READY, HANDED_OFF_TO_ITERVIEW",
        "DOCUMENT_REJECTED, REVIEW_PENDING",
        "NO_RESPONSE, REVIEW_PENDING",
        "REVIEW_PENDING, REVIEWED",
    )
    fun `documented transitions are allowed`(
        from: ApplicationStatus,
        to: ApplicationStatus,
    ) {
        assertThat(from.transitionTo(to)).isEqualTo(to)
    }

    @ParameterizedTest
    @CsvSource(
        "INTERESTED, SUBMITTED",
        "SUBMITTED, PREPARING",
        "DOCUMENT_REJECTED, DOCUMENT_PASSED",
        "DOCUMENT_REJECTED, HANDOFF_READY",
        "REVIEWED, REVIEW_PENDING",
        "WITHDRAWN, PREPARING",
        "HANDED_OFF_TO_ITERVIEW, HANDOFF_READY",
    )
    fun `undocumented transitions are rejected`(
        from: ApplicationStatus,
        to: ApplicationStatus,
    ) {
        assertThatThrownBy { from.transitionTo(to) }.isInstanceOf(InvalidStatusTransition::class.java)
    }

    @Test
    fun `terminal states have no outgoing transitions`() {
        assertThat(ApplicationStatus.entries.filter { it.isTerminal })
            .containsExactlyInAnyOrder(HANDED_OFF_TO_ITERVIEW, REVIEWED, WITHDRAWN)
    }

    @Test
    fun `only document passed lineage is handoff eligible`() {
        assertThat(ApplicationStatus.entries.filter { it.isHandoffEligible })
            .containsExactlyInAnyOrder(DOCUMENT_PASSED, HANDOFF_READY)
    }

    @Test
    fun `every status is reachable from INTERESTED`() {
        val reachable = mutableSetOf(INTERESTED)
        val queue = ArrayDeque(listOf(INTERESTED))
        while (queue.isNotEmpty()) {
            queue.removeFirst().allowedTransitions.forEach { if (reachable.add(it)) queue.addLast(it) }
        }
        assertThat(reachable).containsExactlyInAnyOrderElementsOf(ApplicationStatus.entries)
    }

    @Test
    fun `application transition bumps version and emits an event`() {
        val application =
            Application(
                id = ApplicationId(Fixtures.uuid(40)),
                workspaceId = Fixtures.workspaceA,
                snapshotId = JobPostingSnapshotId(Fixtures.uuid(41)),
                company = "ABC",
                roleTitle = "Backend Engineer",
            )
        val at = Instant.parse("2026-09-18T00:00:00Z")

        val (updated, event) = application.transition(PREPARING, at)

        assertThat(updated.status).isEqualTo(PREPARING)
        assertThat(updated.version).isEqualTo(2)
        assertThat(event).isEqualTo(ApplicationStatusEvent(application.id, INTERESTED, PREPARING, at))
    }
}
