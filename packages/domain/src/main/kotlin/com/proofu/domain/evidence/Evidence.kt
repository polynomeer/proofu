package com.proofu.domain.evidence

import com.proofu.domain.common.Confidence
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Revision
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
    /** External location for link-like types (URL, REPOSITORY, COMMIT, METRIC, CERTIFICATE, REFERENCE_LETTER). */
    val uri: String? = null,
    /** Object storage key for FILE. */
    val objectKey: String? = null,
    /** Content of a NOTE; optional description for every other type. */
    val body: String? = null,
    val verification: VerificationStatus = VerificationStatus.UNVERIFIED,
    val sensitivity: Sensitivity = Sensitivity.INTERNAL,
    val confidence: Confidence = Confidence.NONE,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(title.isNotBlank()) { "evidence title must not be blank" }
        when (type) {
            EvidenceType.NOTE -> domainRequire(!body.isNullOrBlank()) { "a note needs a body" }
            EvidenceType.FILE -> domainRequire(!objectKey.isNullOrBlank()) { "a file needs an object key" }
            else ->
                domainRequire(uri?.let { it.startsWith("https://") || it.startsWith("http://") } == true) {
                    "${type.name.lowercase()} evidence needs an absolute http(s) uri"
                }
        }
    }

    /**
     * Verification is a user or external act. AI never raises it, and EXTERNALLY_VERIFIED is
     * reserved for integrations, so a user may only move between these three states.
     */
    fun verifiedByUser(target: VerificationStatus): Evidence {
        domainRequire(target in USER_SETTABLE) { "users may not set verification to $target" }
        return copy(verification = target)
    }

    companion object {
        val USER_SETTABLE =
            setOf(VerificationStatus.UNVERIFIED, VerificationStatus.USER_VERIFIED, VerificationStatus.EXPIRED)
    }
}
