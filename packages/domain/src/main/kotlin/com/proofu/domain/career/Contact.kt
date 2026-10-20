package com.proofu.domain.career

import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.ContactId
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.util.UUID

/** How the user and this person worked together. */
enum class ContactRelation {
    MANAGER,
    PEER,
    REPORT,
    CLIENT,
    PARTNER,
    MENTOR,
    OTHER,
}

/** The kind of career record a contact is tied to. */
enum class ContactLinkType {
    CAREER_ENTRY,
    PROJECT,
}

/**
 * Where the two of them worked together. Stored as (type, id) like
 * [com.proofu.domain.evidence.ClaimSource]; the table keeps two nullable foreign keys and the
 * domain does not care which column a link came from.
 */
data class ContactLink(
    val type: ContactLinkType,
    val id: UUID,
)

/**
 * Someone the user worked with (F14, docs/domain/career-data-model.md §인맥). A contact exists so
 * that a claim can later be confirmed by a person (F15); it is not a connection. There is no
 * invitation, no acceptance and no profile, the person need not use ProofU, and they are not told
 * they were recorded — only a request for an attestation ever reaches them.
 *
 * This is third-party personal data, so [sensitivity] is fixed at [Sensitivity.RESTRICTED] rather
 * than stored: a column would mean the user could lower it and send someone else's details to a
 * model. [mayEnterAiContext] is false whatever the workspace consented to, and a contact is never
 * written to a log.
 */
data class Contact(
    val id: ContactId,
    val workspaceId: WorkspaceId,
    val displayName: String,
    val relation: ContactRelation,
    /** Needed only to ask this person for an attestation; everything else works without it. */
    val email: String? = null,
    val organization: String? = null,
    val role: String? = null,
    val note: String? = null,
    val links: List<ContactLink> = emptyList(),
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(displayName.isNotBlank()) { "contact name must not be blank" }
        domainRequire(displayName.length <= MAX_NAME_LENGTH) {
            "contact name must be at most $MAX_NAME_LENGTH characters"
        }
        domainRequire(organization == null || organization.length <= MAX_NAME_LENGTH) {
            "organization must be at most $MAX_NAME_LENGTH characters"
        }
        domainRequire(role == null || role.length <= MAX_NAME_LENGTH) {
            "role must be at most $MAX_NAME_LENGTH characters"
        }
        domainRequire(email == null || email.isValidAddress()) { "contact email is not an address" }
        domainRequire(links.distinctBy { it.type to it.id }.size == links.size) {
            "contact links must not repeat the same record"
        }
    }

    /** Third-party personal data: never lowered, never settable. */
    val sensitivity: Sensitivity get() = Sensitivity.RESTRICTED

    /** Always false — consent cannot unlock someone else's details. */
    fun mayEnterAiContext(consent: AiConsent): Boolean = sensitivity.allowedInAiContext(consent)

    /** Whether this person can be asked for an attestation (F15) at all. */
    val isReachable: Boolean get() = email != null

    /** The user edited the record; identity and workspace stay. */
    fun revisedTo(next: Contact): Contact {
        domainRequire(next.id == id && next.workspaceId == workspaceId) { "contact identity must not change" }
        return next.copy(revision = revision.next())
    }

    companion object {
        const val MAX_NAME_LENGTH = 120

        /** The link set, built from typed ids; the set is replaced whole, like every career link. */
        fun linksTo(
            careerEntryIds: Collection<CareerEntryId> = emptyList(),
            projectIds: Collection<ProjectId> = emptyList(),
        ): List<ContactLink> =
            careerEntryIds.map { ContactLink(ContactLinkType.CAREER_ENTRY, it.value) } +
                projectIds.map { ContactLink(ContactLinkType.PROJECT, it.value) }

        /**
         * Deliberately loose: an address is only ever used to send one request the user approved,
         * and the provider decides whether it exists. Rejecting valid-but-unusual addresses would
         * lose a contact for no gain.
         */
        private fun String.isValidAddress(): Boolean = isNotBlank() && length <= MAX_EMAIL_LENGTH && matches(ADDRESS)

        const val MAX_EMAIL_LENGTH = 320
        private val ADDRESS = Regex("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")
    }
}
