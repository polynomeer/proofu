package com.proofu.domain.career

import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.ContactId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ContactTest {
    private val ws = Fixtures.workspaceA
    private val id = ContactId(Fixtures.uuid(20))
    private val entryId = CareerEntryId(Fixtures.uuid(21))
    private val projectId = ProjectId(Fixtures.uuid(22))

    private fun contact(
        name: String = "김동료",
        email: String? = null,
        links: List<ContactLink> = emptyList(),
    ) = Contact(
        id = id,
        workspaceId = ws,
        displayName = name,
        relation = ContactRelation.PEER,
        email = email,
        links = links,
    )

    @Test
    fun `a contact needs a name`() {
        assertThatThrownBy { contact(name = " ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { contact(name = "가".repeat(Contact.MAX_NAME_LENGTH + 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { contact().copy(organization = "가".repeat(Contact.MAX_NAME_LENGTH + 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThat(contact().displayName).isEqualTo("김동료")
    }

    @Test
    fun `someone else's details never reach a model, whatever the workspace consented to`() {
        val contact = contact(email = "peer@example.test")
        assertThat(contact.sensitivity).isEqualTo(Sensitivity.RESTRICTED)
        assertThat(contact.mayEnterAiContext(AiConsent.NONE)).isFalse()
        assertThat(contact.mayEnterAiContext(AiConsent.CONFIDENTIAL)).isFalse()
    }

    @Test
    fun `an address is only needed to reach them`() {
        assertThat(contact().isReachable).isFalse()
        assertThat(contact(email = "peer@example.test").isReachable).isTrue()
        assertThatThrownBy { contact(email = "not-an-address") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { contact(email = "two@@example.test") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { contact(email = " ") }.isInstanceOf(DomainRuleViolation::class.java)
        // Unusual but valid: losing a contact over a strict pattern would be the worse failure.
        assertThat(contact(email = "a+b.c@sub.example.co.kr").isReachable).isTrue()
    }

    @Test
    fun `links say where they worked together and never repeat a record`() {
        val links = Contact.linksTo(listOf(entryId), listOf(projectId))
        assertThat(contact(links = links).links).containsExactly(
            ContactLink(ContactLinkType.CAREER_ENTRY, entryId.value),
            ContactLink(ContactLinkType.PROJECT, projectId.value),
        )
        assertThatThrownBy { contact(links = Contact.linksTo(listOf(entryId, entryId))) }
            .isInstanceOf(DomainRuleViolation::class.java)
        // The same uuid under two kinds is two different records, so it is not a repeat.
        assertThat(contact(links = Contact.linksTo(listOf(entryId), listOf(ProjectId(entryId.value)))).links)
            .hasSize(2)
    }

    @Test
    fun `editing bumps the revision and keeps identity`() {
        val contact = contact()
        val edited = contact.revisedTo(contact.copy(organization = "ACME", role = "리드"))
        assertThat(edited.revision).isEqualTo(Revision.INITIAL.next())
        assertThat(edited.organization).isEqualTo("ACME")
        assertThatThrownBy { contact.revisedTo(contact.copy(id = ContactId(Fixtures.uuid(99)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { contact.revisedTo(contact.copy(workspaceId = WorkspaceId(Fixtures.uuid(98)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }
}
