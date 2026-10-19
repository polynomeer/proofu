package com.proofu.api.contact

import com.proofu.domain.career.Contact
import com.proofu.domain.career.ContactLink
import com.proofu.domain.career.ContactRelation
import com.proofu.domain.common.ContactId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/**
 * `contacts` (V20). The links live in `contact_links` and are read and replaced as a set by
 * [ContactService] rather than mapped as a collection, the way career log links are.
 *
 * There is no sensitivity column on purpose: [Contact.sensitivity] is RESTRICTED and derived, so
 * nothing here can lower it (docs/domain/career-data-model.md §인맥).
 */
@Entity
@Table(name = "contacts")
class ContactEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "display_name")
    var displayName: String,
    @Enumerated(EnumType.STRING)
    var relation: ContactRelation,
    var email: String? = null,
    var organization: String? = null,
    var role: String? = null,
    var note: String? = null,
    var revision: Long = 1,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun apply(contact: Contact) {
        require(contact.id.value == id && contact.workspaceId.value == workspaceId) { "entity identity mismatch" }
        displayName = contact.displayName
        relation = contact.relation
        email = contact.email
        organization = contact.organization
        role = contact.role
        note = contact.note
        revision = contact.revision.value
    }

    /** [links] come from `contact_links`, which the entity does not map. */
    fun toDomain(links: List<ContactLink> = emptyList()): Contact =
        Contact(
            id = ContactId(id),
            workspaceId = WorkspaceId(workspaceId),
            displayName = displayName,
            relation = relation,
            email = email,
            organization = organization,
            role = role,
            note = note,
            links = links,
            revision = Revision(revision),
        )

    companion object {
        fun from(contact: Contact): ContactEntity =
            ContactEntity(
                id = contact.id.value,
                workspaceId = contact.workspaceId.value,
                displayName = contact.displayName,
                relation = contact.relation,
                email = contact.email,
                organization = contact.organization,
                role = contact.role,
                note = contact.note,
                revision = contact.revision.value,
            )
    }
}
