package com.proofu.domain.evidence

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.Instant

data class Evidence(
    val id: EvidenceId,
    val workspaceId: WorkspaceId,
    val type: EvidenceType,
    val title: String,
    val source: EvidenceSource,
    val capturedAt: Instant,
    val uri: String? = null,
    val objectKey: String? = null,
    val verification: VerificationStatus = VerificationStatus.UNVERIFIED,
    val sensitivity: Sensitivity = Sensitivity.INTERNAL,
    val confidence: Confidence = Confidence.NONE,
) {
    init {
        domainRequire(title.isNotBlank()) { "evidence title must not be blank" }
        domainRequire(!uri.isNullOrBlank() || !objectKey.isNullOrBlank()) {
            "evidence requires either a uri or an object key"
        }
    }
}
