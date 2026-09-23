package com.proofu.domain.career

import com.proofu.domain.common.CapabilityId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.evidence.VerificationStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CapabilityTest {
    private val ws = WorkspaceId(Fixtures.uuid(1))
    private val id = CapabilityId(Fixtures.uuid(2))
    private val parentId = CapabilityId(Fixtures.uuid(3))

    private fun capability(
        name: String = "분산 시스템 설계",
        definition: String = "장애를 견디는 시스템을 설계하고 운영한다",
        parent: CapabilityId? = null,
    ) = Capability(id, ws, name, definition, CapabilityCategory.SYSTEM_DESIGN, parentId = parent)

    @Test
    fun `a capability needs a name and a definition that says what the person can do`() {
        assertThatThrownBy { capability(name = " ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { capability(definition = "") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { capability(name = "c".repeat(Capability.MAX_NAME_LENGTH + 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { capability().copy(evidenceCriteria = " ") }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `nesting stays inside the workspace and never points at itself`() {
        assertThatThrownBy { capability(parent = id) }.isInstanceOf(DomainRuleViolation::class.java)
        val parent = Capability(parentId, ws, "설계", "시스템을 설계한다", CapabilityCategory.SYSTEM_DESIGN)
        assertThat(capability(parent = parentId).nestedUnder(parent)).isTrue()
        val foreign = parent.copy(workspaceId = WorkspaceId(Fixtures.uuid(9)))
        assertThat(capability(parent = parentId).nestedUnder(foreign)).isFalse()
    }

    @Test
    fun `the level is the user's own and a revision keeps identity`() {
        val self = capability().copy(selfAssessedLevel = ProficiencyLevel.INDEPENDENT)
        assertThat(self.selfAssessedLevel?.rank).isEqualTo(3)
        val next = self.revisedTo(self.copy(selfAssessedLevel = ProficiencyLevel.ADVANCED))
        assertThat(next.revision).isEqualTo(Revision.INITIAL.next())
        assertThatThrownBy { self.revisedTo(self.copy(id = CapabilityId(Fixtures.uuid(8)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `evidence status is derived from what is linked, never invented`() {
        assertThat(CapabilityEvidenceStatus.of(emptyList())).isEqualTo(CapabilityEvidenceStatus.NONE)
        assertThat(CapabilityEvidenceStatus.of(listOf(VerificationStatus.UNVERIFIED)))
            .isEqualTo(CapabilityEvidenceStatus.UNVERIFIED)
        assertThat(CapabilityEvidenceStatus.of(listOf(VerificationStatus.EXPIRED)))
            .isEqualTo(CapabilityEvidenceStatus.UNVERIFIED)
        assertThat(CapabilityEvidenceStatus.of(listOf(VerificationStatus.EXPIRED, VerificationStatus.USER_VERIFIED)))
            .isEqualTo(CapabilityEvidenceStatus.VERIFIED)
        assertThat(CapabilityEvidenceStatus.of(listOf(VerificationStatus.EXTERNALLY_VERIFIED)))
            .isEqualTo(CapabilityEvidenceStatus.VERIFIED)
    }
}
