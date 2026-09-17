package com.proofu.domain.evidence

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire

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

data class Claim(
    val id: ClaimId,
    val workspaceId: WorkspaceId,
    val text: String,
    val type: ClaimType,
    val sensitivity: Sensitivity = Sensitivity.INTERNAL,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(text.isNotBlank()) { "claim text must not be blank" }
    }
}
