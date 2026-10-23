package com.proofu.domain.imports

import com.proofu.domain.common.DomainRuleViolation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ImportPlannerTest {
    /** Deterministic per seed and always hex, so a seed like "z" cannot slip in. */
    private fun hash(seed: String) =
        seed
            .hashCode()
            .toUInt()
            .toString(16)
            .padStart(64, '0')

    private fun application(
        key: String = "acme-backend",
        contentHash: String = hash("a"),
        fields: Map<String, String> =
            mapOf("company" to "ACME", "roleTitle" to "백엔드", "status" to "SUBMITTED"),
        links: Map<String, List<String>> = emptyMap(),
    ) = ImportItem(key, ImportItemType.APPLICATION, contentHash, "APPLICATIONS.md#L42", fields, links)

    private fun posting(key: String = "acme-posting") =
        ImportItem(key, ImportItemType.POSTING, hash("b"), fields = mapOf("company" to "ACME"))

    @Test
    fun `an item needs a key and a real hash`() {
        assertThatThrownBy { application(key = " ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { application(contentHash = "not-a-hash") }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { application(key = "k".repeat(ImportItem.MAX_KEY_LENGTH + 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `a key never seen before is new, and the same content twice is not imported twice`() {
        val item = application()
        assertThat(ImportPlanner.plan(listOf(item)).single().status).isEqualTo(ImportItemStatus.NEW)

        val before = mapOf(item.sourceKey to ImportedBefore(item.contentHash))
        assertThat(ImportPlanner.plan(listOf(item), before).single().status)
            .isEqualTo(ImportItemStatus.UNCHANGED)
    }

    @Test
    fun `the archive moving on is a change, and moving on after an edit here is a conflict`() {
        val item = application(contentHash = hash("c"))
        val moved = mapOf(item.sourceKey to ImportedBefore(hash("a")))
        assertThat(ImportPlanner.plan(listOf(item), moved).single().status)
            .isEqualTo(ImportItemStatus.CHANGED)

        val alsoEditedHere = mapOf(item.sourceKey to ImportedBefore(hash("a"), editedSince = true))
        assertThat(ImportPlanner.plan(listOf(item), alsoEditedHere).single().status)
            .isEqualTo(ImportItemStatus.CONFLICT)
    }

    @Test
    fun `a rejected item says why, in terms of the file that was sent`() {
        val missing = application(fields = mapOf("company" to "ACME"))
        val entry = ImportPlanner.plan(listOf(missing)).single()
        assertThat(entry.status).isEqualTo(ImportItemStatus.REJECTED)
        assertThat(entry.reason).contains("roleTitle").contains("status")

        val dangling = application(links = mapOf("posting" to listOf("없는-공고")))
        assertThat(ImportPlanner.plan(listOf(dangling)).single().reason).contains("없는-공고")

        val twice = ImportPlanner.plan(listOf(application(), application(contentHash = hash("z"))))
        assertThat(twice.map { it.status })
            .containsExactly(ImportItemStatus.NEW, ImportItemStatus.REJECTED)
        assertThat(twice.last().reason).contains("두 번")
    }

    @Test
    fun `a link that the bundle does carry is fine`() {
        val plan =
            ImportPlanner.plan(
                listOf(posting(), application(links = mapOf("posting" to listOf("acme-posting")))),
            )
        assertThat(plan.map { it.status }).containsOnly(ImportItemStatus.NEW)
    }

    @Test
    fun `what this stage cannot apply is reported, not silently dropped`() {
        val note = ImportItem("note-1", ImportItemType.NOTE, hash("d"), fields = mapOf("occurredAt" to "2026-10-01"))
        val entry = ImportPlanner.plan(listOf(note)).single()
        assertThat(entry.status).isEqualTo(ImportItemStatus.REJECTED)
        assertThat(entry.reason).contains("note")
        // The user still sees it in the plan, so nothing disappears without a word.
        assertThat(ImportPlanner.summary(listOf(entry))).containsEntry(ImportItemStatus.REJECTED, 1)
    }

    @Test
    fun `only what is new is selected by default`() {
        val item = application()
        val entries =
            ImportPlanner.plan(
                listOf(item, posting()),
                mapOf(item.sourceKey to ImportedBefore(item.contentHash)),
            )
        assertThat(entries.map { it.selectedByDefault }).containsExactly(false, true)
        assertThat(ImportPlanner.summary(entries))
            .containsEntry(ImportItemStatus.UNCHANGED, 1)
            .containsEntry(ImportItemStatus.NEW, 1)
    }
}
