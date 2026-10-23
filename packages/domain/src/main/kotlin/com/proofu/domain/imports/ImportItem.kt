package com.proofu.domain.imports

import com.proofu.domain.common.domainRequire

/** What a bundle item becomes. Stage 1 applies APPLICATION and POSTING; the rest are planned only. */
enum class ImportItemType {
    APPLICATION,
    POSTING,
    DOCUMENT,
    CAREER,
    NOTE,
}

/** The verdict a plan reaches for one item, before the user decides anything. */
enum class ImportItemStatus {
    /** Never seen this key in this workspace. */
    NEW,

    /** Same key, same content: already in, nothing to do. */
    UNCHANGED,

    /** Same key, different content: the archive moved on. */
    CHANGED,

    /** Changed in the bundle and edited here since it was imported. The user decides. */
    CONFLICT,

    /** The item cannot become anything; [ImportPlanEntry.reason] says why. */
    REJECTED,
}

enum class ImportRunStatus {
    PLANNED,
    APPLIED,
    FAILED,
}

/**
 * One item of a bundle, as the adapter wrote it (docs/product/personal-archive-import.md).
 *
 * [sourceKey] is the adapter's own name for the thing and is what makes a second import safe:
 * the same key with the same [contentHash] is [ImportItemStatus.UNCHANGED]. [sourcePath] is for
 * the person reading the plan — a row nobody can trace back is a row nobody can judge.
 */
data class ImportItem(
    val sourceKey: String,
    val type: ImportItemType,
    val contentHash: String,
    val sourcePath: String? = null,
    /** Structured values the adapter translated into ProofU's own vocabulary. */
    val fields: Map<String, String> = emptyMap(),
    /** Keys of other items this one belongs to, by role: "posting", "documents". */
    val links: Map<String, List<String>> = emptyMap(),
) {
    init {
        domainRequire(sourceKey.isNotBlank()) { "import item needs a source key" }
        domainRequire(sourceKey.length <= MAX_KEY_LENGTH) {
            "source key must be at most $MAX_KEY_LENGTH characters"
        }
        domainRequire(contentHash.matches(SHA256)) { "content hash must be sha256 hex" }
    }

    fun field(name: String): String? = fields[name]?.trim()?.ifEmpty { null }

    /**
     * What this item is missing before it can become anything. Empty means it can be planned —
     * not that it will be accepted, which is the planner's call.
     */
    fun missingFields(): List<String> = REQUIRED_FIELDS[type].orEmpty().filter { field(it) == null }

    companion object {
        const val MAX_KEY_LENGTH = 200
        private val SHA256 = Regex("[0-9a-f]{64}")

        /**
         * The least an item needs to be worth a row. A posting carries its text as a file, so the
         * manifest only has to say whose posting it is.
         */
        val REQUIRED_FIELDS: Map<ImportItemType, List<String>> =
            mapOf(
                ImportItemType.APPLICATION to listOf("company", "roleTitle", "status"),
                ImportItemType.POSTING to listOf("company"),
                ImportItemType.DOCUMENT to listOf("title", "type"),
                ImportItemType.CAREER to listOf("title", "startDate"),
                ImportItemType.NOTE to listOf("occurredAt"),
            )
    }
}
