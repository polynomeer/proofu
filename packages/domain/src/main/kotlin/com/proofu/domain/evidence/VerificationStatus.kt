package com.proofu.domain.evidence

/** Only a user or an external verifier may raise this. AI never does. */
enum class VerificationStatus {
    UNVERIFIED,
    USER_VERIFIED,
    EXTERNALLY_VERIFIED,
    EXPIRED,
    ;

    val isVerified: Boolean get() = this == USER_VERIFIED || this == EXTERNALLY_VERIFIED
}
