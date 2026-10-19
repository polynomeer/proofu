package com.proofu.api.contact

import com.proofu.domain.career.Contact
import com.proofu.domain.career.ContactLink
import com.proofu.domain.career.ContactLinkType
import com.proofu.domain.career.ContactRelation
import com.proofu.domain.common.ContactId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Matches `ContactInput` in the contract. */
data class ContactRequest(
    @field:NotBlank
    @field:Size(max = Contact.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
    val displayName: String?,
    @field:NotNull val relation: ContactRelation?,
    @field:Email
    @field:Size(max = Contact.MAX_EMAIL_LENGTH)
    val email: String? = null,
    @field:Size(max = Contact.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
    val organization: String? = null,
    @field:Size(max = Contact.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
    val role: String? = null,
    @field:Size(max = MAX_NOTE_LENGTH, message = "{max}자 이하로 입력하세요")
    val note: String? = null,
    @field:Size(max = MAX_LINKS) val careerEntryIds: List<UUID> = emptyList(),
    @field:Size(max = MAX_LINKS) val projectIds: List<UUID> = emptyList(),
    val revision: Long? = null,
) {
    fun links(): List<ContactLink> =
        careerEntryIds.distinct().map { ContactLink(ContactLinkType.CAREER_ENTRY, it) } +
            projectIds.distinct().map { ContactLink(ContactLinkType.PROJECT, it) }

    fun toDomain(
        id: ContactId,
        workspaceId: WorkspaceId,
        revision: Revision,
    ): Contact =
        Contact(
            id = id,
            workspaceId = workspaceId,
            displayName = requireNotNull(displayName).trim(),
            relation = requireNotNull(relation),
            email = email?.trim()?.ifEmpty { null },
            organization = organization?.trim()?.ifEmpty { null },
            role = role?.trim()?.ifEmpty { null },
            note = note?.trim()?.ifEmpty { null },
            links = links(),
            revision = revision,
        )
}

data class ContactResponse(
    val id: UUID,
    val displayName: String,
    val relation: ContactRelation,
    val email: String?,
    val organization: String?,
    val role: String?,
    val note: String?,
    val careerEntryIds: List<UUID>,
    val projectIds: List<UUID>,
    /** Whether this person can be asked for an attestation at all (F15). */
    val reachable: Boolean,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: ContactEntity,
            links: List<ContactLink>,
        ): ContactResponse =
            ContactResponse(
                id = e.id,
                displayName = e.displayName,
                relation = e.relation,
                email = e.email,
                organization = e.organization,
                role = e.role,
                note = e.note,
                careerEntryIds = links.filter { it.type == ContactLinkType.CAREER_ENTRY }.map { it.id },
                projectIds = links.filter { it.type == ContactLinkType.PROJECT }.map { it.id },
                reachable = e.email != null,
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class ContactPage(
    val items: List<ContactResponse>,
    val nextCursor: String?,
)

const val MAX_NOTE_LENGTH = 2000
const val MAX_LINKS = 20
