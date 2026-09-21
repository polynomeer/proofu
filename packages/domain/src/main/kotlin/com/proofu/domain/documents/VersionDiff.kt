package com.proofu.domain.documents

enum class DiffKind {
    ADDED,
    REMOVED,
    CHANGED,
    UNCHANGED,
}

enum class SegmentKind {
    SAME,
    ADDED,
    REMOVED,
}

/** A run of words in a changed block's text: what the target kept, added or dropped versus the base. */
data class DiffSegment(
    val kind: SegmentKind,
    val text: String,
)

data class BlockDiff(
    val blockId: String,
    val kind: DiffKind,
    /** Which aspects changed: text, certainty, approval, references. Empty unless CHANGED. */
    val changedFields: List<String>,
    val base: GeneratedBlock?,
    val target: GeneratedBlock?,
    val textDiff: List<DiffSegment>,
)

data class VersionDiff(
    val entries: List<BlockDiff>,
) {
    val added: Int get() = entries.count { it.kind == DiffKind.ADDED }
    val removed: Int get() = entries.count { it.kind == DiffKind.REMOVED }
    val changed: Int get() = entries.count { it.kind == DiffKind.CHANGED }
    val unchanged: Int get() = entries.count { it.kind == DiffKind.UNCHANGED }

    companion object {
        /**
         * R02 "비교": block-by-block, in the target's order with removed blocks appended in the
         * base's order. Text differences are word-level (whitespace-delimited LCS).
         */
        fun compare(
            base: GeneratedOutput,
            target: GeneratedOutput,
        ): VersionDiff {
            val baseById = base.blocks.associateBy { it.blockId }
            val targetIds = target.blocks.map { it.blockId }.toSet()
            val entries =
                target.blocks.map { t ->
                    val b = baseById[t.blockId]
                    when {
                        b == null ->
                            BlockDiff(
                                t.blockId,
                                DiffKind.ADDED,
                                emptyList(),
                                null,
                                t,
                                listOf(DiffSegment(SegmentKind.ADDED, t.text)),
                            )
                        else -> {
                            val fields = changedFields(b, t)
                            if (fields.isEmpty()) {
                                BlockDiff(
                                    t.blockId,
                                    DiffKind.UNCHANGED,
                                    emptyList(),
                                    b,
                                    t,
                                    listOf(DiffSegment(SegmentKind.SAME, t.text)),
                                )
                            } else {
                                BlockDiff(t.blockId, DiffKind.CHANGED, fields, b, t, wordDiff(b.text, t.text))
                            }
                        }
                    }
                } +
                    base.blocks.filter { it.blockId !in targetIds }.map { b ->
                        BlockDiff(
                            b.blockId,
                            DiffKind.REMOVED,
                            emptyList(),
                            b,
                            null,
                            listOf(DiffSegment(SegmentKind.REMOVED, b.text)),
                        )
                    }
            return VersionDiff(entries)
        }

        private fun changedFields(
            b: GeneratedBlock,
            t: GeneratedBlock,
        ): List<String> =
            listOfNotNull(
                "text".takeIf { b.text != t.text },
                "certainty".takeIf { b.certainty != t.certainty },
                "approval".takeIf { b.approvedByUser != t.approvedByUser },
                "references".takeIf {
                    b.claimRefs != t.claimRefs ||
                        b.evidenceRefs != t.evidenceRefs ||
                        b.requirementRefs != t.requirementRefs
                },
            )

        /** Longest-common-subsequence over words; adjacent segments of one kind are merged. */
        fun wordDiff(
            from: String,
            to: String,
        ): List<DiffSegment> {
            val a = tokenize(from)
            val b = tokenize(to)
            val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
            for (i in a.indices.reversed()) {
                for (j in b.indices.reversed()) {
                    lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
            val out = mutableListOf<DiffSegment>()

            fun push(
                kind: SegmentKind,
                word: String,
            ) {
                val last = out.lastOrNull()
                if (last != null && last.kind == kind) {
                    out[out.size - 1] = DiffSegment(kind, last.text + word)
                } else {
                    out += DiffSegment(kind, word)
                }
            }
            var i = 0
            var j = 0
            while (i < a.size && j < b.size) {
                when {
                    a[i] == b[j] -> {
                        push(SegmentKind.SAME, a[i])
                        i++
                        j++
                    }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> {
                        push(SegmentKind.REMOVED, a[i])
                        i++
                    }
                    else -> {
                        push(SegmentKind.ADDED, b[j])
                        j++
                    }
                }
            }
            while (i < a.size) push(SegmentKind.REMOVED, a[i++])
            while (j < b.size) push(SegmentKind.ADDED, b[j++])
            return out
        }

        /** Words with their trailing whitespace, so segments concatenate back to the original text. */
        private fun tokenize(text: String): List<String> =
            Regex("\\S+\\s*|\\s+").findAll(text).map { it.value }.toList()
    }
}
