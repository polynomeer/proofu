package com.proofu.domain.evidence

import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Fixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class EvidenceTest {
    private fun evidence(
        type: EvidenceType,
        uri: String? = null,
        objectKey: String? = null,
        body: String? = null,
    ) = Evidence(
        id = EvidenceId(Fixtures.uuid(70)),
        workspaceId = Fixtures.workspaceA,
        type = type,
        title = "Retro doc",
        source = EvidenceSource.USER_INPUT,
        capturedAt = Instant.EPOCH,
        uri = uri,
        objectKey = objectKey,
        body = body,
    )

    @Test
    fun `location rule depends on the type`() {
        assertThat(evidence(EvidenceType.NOTE, body = "Shipped on time").body).isNotBlank()
        assertThatThrownBy {
            evidence(
                EvidenceType.NOTE,
                uri = "https://x",
            )
        }.isInstanceOf(DomainRuleViolation::class.java)

        assertThat(evidence(EvidenceType.FILE, objectKey = "ws/a.pdf").objectKey).isNotBlank()
        assertThatThrownBy {
            evidence(
                EvidenceType.FILE,
                uri = "https://x",
            )
        }.isInstanceOf(DomainRuleViolation::class.java)

        assertThat(evidence(EvidenceType.URL, uri = "https://example.com").uri).isNotBlank()
        assertThatThrownBy { evidence(EvidenceType.URL, body = "text") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy {
            evidence(EvidenceType.REPOSITORY, uri = "github.com/x")
        }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `users can verify, unverify or expire but not externally verify`() {
        val e = evidence(EvidenceType.URL, uri = "https://example.com")
        assertThat(
            e.verifiedByUser(VerificationStatus.USER_VERIFIED).verification,
        ).isEqualTo(VerificationStatus.USER_VERIFIED)
        assertThat(e.verifiedByUser(VerificationStatus.EXPIRED).verification).isEqualTo(VerificationStatus.EXPIRED)
        assertThatThrownBy { e.verifiedByUser(VerificationStatus.EXTERNALLY_VERIFIED) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }
}
