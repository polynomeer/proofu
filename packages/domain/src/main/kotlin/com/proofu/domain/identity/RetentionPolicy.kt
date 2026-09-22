package com.proofu.domain.identity

import com.proofu.domain.common.domainRequire
import java.time.Duration
import java.time.Instant

/**
 * How long a workspace keeps what the user threw away and what it rendered for them
 * (docs/data/retention.md §보존 기간 설정). Submission snapshots, audit events and the other
 * immutable tables are not covered: they live as long as the account does.
 */
data class RetentionPolicy(
    /** Soft-deleted source records are purged after this many days. */
    val trashDays: Int = DEFAULT_TRASH_DAYS,
    /** Export files (document renders and full-data ZIPs) expire after this many days. */
    val exportDays: Int = DEFAULT_EXPORT_DAYS,
) {
    init {
        domainRequire(trashDays in TRASH_DAYS_RANGE) { "trashDays must be within $TRASH_DAYS_RANGE" }
        domainRequire(exportDays in EXPORT_DAYS_RANGE) { "exportDays must be within $EXPORT_DAYS_RANGE" }
    }

    /** Rows deleted before this instant are due for purging. */
    fun trashCutoff(now: Instant): Instant = now.minus(Duration.ofDays(trashDays.toLong()))

    /** Export rows created before this instant are due for expiry. */
    fun exportCutoff(now: Instant): Instant = now.minus(Duration.ofDays(exportDays.toLong()))

    /** When an export made now stops being downloadable. */
    fun exportExpiry(createdAt: Instant): Instant = createdAt.plus(Duration.ofDays(exportDays.toLong()))

    companion object {
        const val DEFAULT_TRASH_DAYS = 30
        const val DEFAULT_EXPORT_DAYS = 7
        val TRASH_DAYS_RANGE = 7..365
        val EXPORT_DAYS_RANGE = 1..90
        val DEFAULT = RetentionPolicy()
    }
}
