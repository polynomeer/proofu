package com.proofu.domain.evidence

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.util.UUID

enum class ClaimType {
    FACT,
    INFERENCE,
    OPINION,
}

/** Derived from linked evidence; a claim without evidence is UNSUPPORTED. */
enum class ClaimStatus {
    UNSUPPORTED,
    SUPPORTED,
    CONTESTED,
}

enum class ClaimSourceType {
    CAREER_ENTRY,
    PROJECT,
    ACHIEVEMENT,
}

/** The career record a claim is made about, pinned to the revision it was read at. */
data class ClaimSource(
    val type: ClaimSourceType,
    val id: UUID,
    val revision: Revision,
)

data class Claim(
    val id: ClaimId,
    val workspaceId: WorkspaceId,
    val text: String,
    val type: ClaimType,
    val sensitivity: Sensitivity = Sensitivity.INTERNAL,
    val sources: List<ClaimSource> = emptyList(),
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(text.isNotBlank()) { "claim text must not be blank" }
        domainRequire(sources.distinctBy { it.type to it.id }.size == sources.size) {
            "claim sources must not repeat the same record"
        }
    }
}
