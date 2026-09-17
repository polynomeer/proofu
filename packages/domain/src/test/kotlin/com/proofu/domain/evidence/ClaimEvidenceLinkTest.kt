package com.proofu.domain.evidence

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Confidence
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.WorkspaceBoundaryViolation
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class ClaimEvidenceLinkTest {
    private fun claim(workspace: WorkspaceId) =
        Claim(ClaimId(Fixtures.uuid(30)), workspace, "Led the migration", ClaimType.FACT)

    private fun evidence(workspace: WorkspaceId) =
        Evidence(
            id = EvidenceId(Fixtures.uuid(31)),
            workspaceId = workspace,
            type = EvidenceType.URL,
            title = "Migration retrospective",
            source = EvidenceSource.USER_INPUT,
            capturedAt = Instant.EPOCH,
            uri = "https://example.com/retro",
        )

    @Test
    fun `evidence from another workspace cannot be linked`() {
        assertThatThrownBy {
            ClaimEvidenceLink.link(
                claim(Fixtures.workspaceA),
                evidence(Fixtures.workspaceB),
                EvidenceRelation.SUPPORTS,
                Confidence.FULL,
            )
        }.isInstanceOf(WorkspaceBoundaryViolation::class.java)
    }

    @Test
    fun `same workspace link is created`() {
        val link =
            ClaimEvidenceLink.link(
                claim(Fixtures.workspaceA),
                evidence(Fixtures.workspaceA),
                EvidenceRelation.SUPPORTS,
                Confidence(0.9),
            )
        assertThat(link.claimId).isEqualTo(ClaimId(Fixtures.uuid(30)))
    }

    @Test
    fun `partial support requires a scope`() {
        assertThatThrownBy {
            ClaimEvidenceLink(
                ClaimId(Fixtures.uuid(30)),
                EvidenceId(Fixtures.uuid(31)),
                EvidenceRelation.PARTIALLY_SUPPORTS,
                Confidence.FULL,
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `claim status is derived from links`() {
        val supports =
            ClaimEvidenceLink(
                ClaimId(Fixtures.uuid(30)),
                EvidenceId(Fixtures.uuid(31)),
                EvidenceRelation.SUPPORTS,
                Confidence.FULL,
            )
        val refutes = supports.copy(evidenceId = EvidenceId(Fixtures.uuid(32)), relation = EvidenceRelation.REFUTES)

        assertThat(ClaimEvidenceLink.statusOf(emptyList())).isEqualTo(ClaimStatus.UNSUPPORTED)
        assertThat(ClaimEvidenceLink.statusOf(listOf(supports))).isEqualTo(ClaimStatus.SUPPORTED)
        assertThat(ClaimEvidenceLink.statusOf(listOf(supports, refutes))).isEqualTo(ClaimStatus.CONTESTED)
    }

    @Test
    fun `evidence needs a uri or an object key`() {
        assertThatThrownBy {
            Evidence(
                id = EvidenceId(Fixtures.uuid(33)),
                workspaceId = Fixtures.workspaceA,
                type = EvidenceType.FILE,
                title = "Certificate",
                source = EvidenceSource.USER_INPUT,
                capturedAt = Instant.EPOCH,
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
    }
}
