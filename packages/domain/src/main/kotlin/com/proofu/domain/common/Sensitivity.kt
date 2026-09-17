package com.proofu.domain.common

/**
 * Data classification (security §12.1). Drives document generation and
 * external transfer filters, including what may enter an AI context.
 */
enum class Sensitivity {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
    ;

    /** CONFIDENTIAL and RESTRICTED data never enter an AI context without explicit consent. */
    val allowedInAiContextByDefault: Boolean
        get() = this == PUBLIC || this == INTERNAL
}
