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
        links: Map<String, List<String>> = mapOf("posting" to listOf("acme-posting")),
    ) = ImportItem(key, ImportItemType.APPLICATION, contentHash, "APPLICATIONS.md#L42", fields, links)

    /** Every bundle here carries the posting, because an application without one is refused. */
    private fun planOf(
        vararg items: ImportItem,
        imported: Map<String, ImportedBefore> = emptyMap(),
    ) = ImportPlanner.plan(listOf(posting()) + items, imported)

    private fun applicationEntry(entries: List<ImportPlanEntry>) =
        entries.first { it.item.type == ImportItemType.APPLICATION }

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
        assertThat(applicationEntry(planOf(item)).status).isEqualTo(ImportItemStatus.NEW)

        val before = mapOf(item.sourceKey to ImportedBefore(item.contentHash))
        assertThat(applicationEntry(planOf(item, imported = before)).status)
            .isEqualTo(ImportItemStatus.UNCHANGED)
    }

    @Test
    fun `the archive moving on is a change, and moving on after an edit here is a conflict`() {
        val item = application(contentHash = hash("c"))
        val moved = mapOf(item.sourceKey to ImportedBefore(hash("a")))
        assertThat(applicationEntry(planOf(item, imported = moved)).status)
            .isEqualTo(ImportItemStatus.CHANGED)

        val alsoEditedHere = mapOf(item.sourceKey to ImportedBefore(hash("a"), editedSince = true))
        assertThat(applicationEntry(planOf(item, imported = alsoEditedHere)).status)
            .isEqualTo(ImportItemStatus.CONFLICT)
    }

    @Test
    fun `a rejected item says why, in terms of the file that was sent`() {
        val missing = application(fields = mapOf("company" to "ACME"))
        val entry = applicationEntry(planOf(missing))
        assertThat(entry.status).isEqualTo(ImportItemStatus.REJECTED)
        assertThat(entry.reason).contains("roleTitle").contains("status")

        val dangling = application(links = mapOf("posting" to listOf("없는-공고")))
        assertThat(applicationEntry(planOf(dangling)).reason).contains("없는-공고")

        val twice = planOf(application(), application(contentHash = hash("f")))
        assertThat(twice.map { it.status })
            .containsExactly(ImportItemStatus.NEW, ImportItemStatus.NEW, ImportItemStatus.REJECTED)
        assertThat(twice.last().reason).contains("두 번")
    }

    @Test
    fun `a link that the bundle does carry is fine`() {
        assertThat(planOf(application()).map { it.status }).containsOnly(ImportItemStatus.NEW)

        // A posting that came in with an earlier bundle still counts.
        val earlier = mapOf("acme-posting" to ImportedBefore(hash("b")))
        assertThat(ImportPlanner.plan(listOf(application()), earlier).single().status)
            .isEqualTo(ImportItemStatus.NEW)
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
    fun `a past application cannot be recorded without the posting it was for`() {
        val entry = ImportPlanner.plan(listOf(application(links = emptyMap()))).single()
        assertThat(entry.status).isEqualTo(ImportItemStatus.REJECTED)
        assertThat(entry.reason).contains("공고가 없습니다").contains("posting")
    }

    @Test
    fun `only what is new is selected by default`() {
        val item = application()
        val entries = planOf(item, imported = mapOf(item.sourceKey to ImportedBefore(item.contentHash)))
        assertThat(entries.map { it.selectedByDefault }).containsExactly(true, false)
        assertThat(ImportPlanner.summary(entries))
            .containsEntry(ImportItemStatus.UNCHANGED, 1)
            .containsEntry(ImportItemStatus.NEW, 1)
    }
}
