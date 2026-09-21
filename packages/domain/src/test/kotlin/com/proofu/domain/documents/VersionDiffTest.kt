package com.proofu.domain.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.Fixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionDiffTest {
    private val claim = ClaimId(Fixtures.uuid(1))

    private fun block(
        id: String,
        text: String,
        certainty: Certainty = Certainty.SUPPORTED,
        approved: Boolean = false,
        claims: Set<ClaimId> = setOf(claim),
    ) = GeneratedBlock(id, text, claimRefs = claims, certainty = certainty, approvedByUser = approved)

    @Test
    fun `classifies blocks as added, removed, changed or unchanged in target order`() {
        val base = GeneratedOutput(listOf(block("a-1", "같은 문장"), block("a-2", "지워질 문장"), block("b-1", "바뀔 문장 하나")))
        val target =
            GeneratedOutput(
                listOf(
                    block("b-1", "바뀐 문장 둘", certainty = Certainty.INFERRED, approved = true),
                    block("a-1", "같은 문장"),
                    block("c-1", "새 문장", claims = emptySet()),
                ),
            )

        val diff = VersionDiff.compare(base, target)

        assertThat(diff.entries.map { it.blockId to it.kind }).containsExactly(
            "b-1" to DiffKind.CHANGED,
            "a-1" to DiffKind.UNCHANGED,
            "c-1" to DiffKind.ADDED,
            "a-2" to DiffKind.REMOVED,
        )
        assertThat(diff.entries[0].changedFields).containsExactly("text", "certainty", "approval")
        assertThat(diff.added).isEqualTo(1)
        assertThat(diff.removed).isEqualTo(1)
        assertThat(diff.changed).isEqualTo(1)
        assertThat(diff.unchanged).isEqualTo(1)
    }

    @Test
    fun `word diff keeps common words and concatenates back to both texts`() {
        val segments = VersionDiff.wordDiff("온보딩 플로우를 5단계에서 2단계로 축소했습니다.", "온보딩을 5단계에서 2단계로 크게 축소했습니다.")
        assertThat(segments.map { it.kind }).containsExactly(
            SegmentKind.REMOVED,
            SegmentKind.ADDED,
            SegmentKind.SAME,
            SegmentKind.ADDED,
            SegmentKind.SAME,
        )
        assertThat(segments.filter { it.kind != SegmentKind.ADDED }.joinToString("") { it.text })
            .isEqualTo("온보딩 플로우를 5단계에서 2단계로 축소했습니다.")
        assertThat(segments.filter { it.kind != SegmentKind.REMOVED }.joinToString("") { it.text })
            .isEqualTo("온보딩을 5단계에서 2단계로 크게 축소했습니다.")
        assertThat(VersionDiff.wordDiff("", "새 문장")).containsExactly(DiffSegment(SegmentKind.ADDED, "새 문장"))
    }

    @Test
    fun `reference changes count as a change even when the text is identical`() {
        val base = GeneratedOutput(listOf(block("a-1", "문장")))
        val target = GeneratedOutput(listOf(block("a-1", "문장", claims = emptySet())))
        val entry = VersionDiff.compare(base, target).entries.single()
        assertThat(entry.kind).isEqualTo(DiffKind.CHANGED)
        assertThat(entry.changedFields).containsExactly("references")
        assertThat(entry.textDiff).containsExactly(DiffSegment(SegmentKind.SAME, "문장"))
    }
}
