package com.proofu.domain.career

import com.proofu.domain.common.CapabilityId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import com.proofu.domain.evidence.VerificationStatus

/**
 * How well the linked evidence backs a capability. Derived, never stored: the tool reports what
 * is attached and lets the user judge the level (docs/domain/career-data-model.md §역량).
 */
enum class CapabilityEvidenceStatus {
    NONE,
    UNVERIFIED,
    VERIFIED,
    ;

    companion object {
        fun of(linked: Collection<VerificationStatus>): CapabilityEvidenceStatus =
            when {
                linked.isEmpty() -> NONE
                linked.any { it.isVerified } -> VERIFIED
                else -> UNVERIFIED
            }
    }
}

/**
 * An ability stated as a definition, optionally nested under a broader one. The level is the
 * user's own assessment; evidence is reported beside it rather than converted into a level.
 */
data class Capability(
    val id: CapabilityId,
    val workspaceId: WorkspaceId,
    val name: String,
    val definition: String,
    val category: CapabilityCategory,
    val selfAssessedLevel: ProficiencyLevel? = null,
    val evidenceCriteria: String? = null,
    val parentId: CapabilityId? = null,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(name.isNotBlank()) { "capability name must not be blank" }
        domainRequire(name.length <= MAX_NAME_LENGTH) { "capability name must be at most $MAX_NAME_LENGTH characters" }
        domainRequire(definition.isNotBlank()) { "capability definition must not be blank" }
        domainRequire(evidenceCriteria == null || evidenceCriteria.isNotBlank()) {
            "evidence criteria must not be blank when given"
        }
        domainRequire(parentId != id) { "a capability cannot be its own parent" }
    }

    /** A capability may only sit under one of its own workspace. */
    fun nestedUnder(parent: Capability): Boolean = parentId == parent.id && workspaceId == parent.workspaceId

    fun revisedTo(next: Capability): Capability {
        domainRequire(next.id == id && next.workspaceId == workspaceId) { "capability identity must not change" }
        return next.copy(revision = revision.next())
    }

    companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
