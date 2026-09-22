package com.proofu.domain.identity

import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.Instant

/**
 * Per-workspace preferences (S01): what AI may see, what new career records default to, and how
 * long the trash and export files are kept.
 */
data class WorkspaceSettings(
    val workspaceId: WorkspaceId,
    val aiConsent: AiConsent = AiConsent.NONE,
    /** When the user last granted consent; null while NONE. Kept as the record of the decision. */
    val aiConsentAt: Instant? = null,
    val defaultVisibility: Visibility = Visibility.PRIVATE,
    val retention: RetentionPolicy = RetentionPolicy.DEFAULT,
    val version: Long = 0,
) {
    init {
        domainRequire((aiConsent == AiConsent.NONE) == (aiConsentAt == null)) {
            "aiConsentAt must be set exactly when consent is granted"
        }
    }

    /** Granting consent is the sensitive direction; the caller proves a recent login for it. */
    fun grantsConsentOver(previous: WorkspaceSettings): Boolean =
        previous.aiConsent == AiConsent.NONE && aiConsent != AiConsent.NONE
}
