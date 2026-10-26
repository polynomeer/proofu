package com.proofu.domain.imports

/** What the ledger already knows about a key in this workspace. */
data class ImportedBefore(
    val contentHash: String,
    /** True when the row this key produced was edited in ProofU after it was imported. */
    val editedSince: Boolean = false,
)

/** One line of a plan: the verdict, and why, before the user picks anything. */
data class ImportPlanEntry(
    val item: ImportItem,
    val status: ImportItemStatus,
    val reason: String? = null,
) {
    /** Only what is actually new or changed is worth the user's attention by default. */
    val selectedByDefault: Boolean
        get() = status == ImportItemStatus.NEW
}

/**
 * Decides what a bundle would do, without doing any of it
 * (docs/product/personal-archive-import.md §흐름).
 *
 * Nothing here writes, and nothing here is clever: an item is rejected for reasons a person can
 * read back against the file they sent, and everything else is decided by comparing the key and
 * the hash with what was imported before. The planner is pure so the rules can be argued with in
 * a unit test rather than through a 214-row bundle.
 */
object ImportPlanner {
    /** Types stage 1 can actually apply; the rest are planned and reported, not applied. */
    val APPLICABLE: Set<ImportItemType> = setOf(ImportItemType.APPLICATION, ImportItemType.POSTING)

    fun plan(
        items: List<ImportItem>,
        imported: Map<String, ImportedBefore> = emptyMap(),
        applicable: Set<ImportItemType> = APPLICABLE,
    ): List<ImportPlanEntry> {
        val seen = mutableSetOf<String>()
        val keys = items.map { it.sourceKey }.toSet()
        return items.map { item ->
            val reason =
                rejection(
                    item,
                    keys,
                    repeated = !seen.add(item.sourceKey),
                    applicable = applicable,
                    importedKeys = imported.keys,
                )
            when {
                reason != null -> ImportPlanEntry(item, ImportItemStatus.REJECTED, reason)
                else -> ImportPlanEntry(item, verdict(item, imported[item.sourceKey]))
            }
        }
    }

    private fun verdict(
        item: ImportItem,
        before: ImportedBefore?,
    ): ImportItemStatus =
        when {
            before == null -> ImportItemStatus.NEW
            before.contentHash == item.contentHash -> ImportItemStatus.UNCHANGED
            before.editedSince -> ImportItemStatus.CONFLICT
            else -> ImportItemStatus.CHANGED
        }

    /** Why this item cannot become anything, in words the sender can check against their file. */
    private fun rejection(
        item: ImportItem,
        keys: Set<String>,
        repeated: Boolean,
        applicable: Set<ImportItemType>,
        importedKeys: Set<String>,
    ): String? {
        if (repeated) return "같은 source key가 번들에 두 번 있습니다: ${item.sourceKey}"
        val missing = item.missingFields()
        if (missing.isNotEmpty()) return "필요한 값이 없습니다: ${missing.joinToString(", ")}"
        // A key the ledger already applied is not dangling: a second import may link to a posting
        // that came in with the first one.
        val dangling =
            item.links.values
                .flatten()
                .filterNot { it in keys || it in importedKeys }
        if (dangling.isNotEmpty()) return "번들에 없는 항목을 가리킵니다: ${dangling.joinToString(", ")}"
        if (item.type !in applicable) return "이 단계에서는 ${item.type.name.lowercase()} 항목을 아직 들이지 않습니다"
        if (item.type == ImportItemType.APPLICATION && !item.citesPosting) {
            return "공고가 없습니다. 지원 건은 공고 원문 없이 만들 수 없으므로, " +
                "가진 만큼이라도 posting 항목으로 함께 보내고 links.posting으로 가리키세요"
        }
        return null
    }

    /** What the user is asked to confirm before anything is written. */
    fun summary(entries: List<ImportPlanEntry>): Map<ImportItemStatus, Int> =
        entries.groupingBy { it.status }.eachCount()
}
