package com.proofu.domain.career

import com.proofu.domain.common.Revision
import com.proofu.domain.common.SkillId
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.LocalDate

/**
 * One technology, tool or language the user can claim, gathered under a single name so the same
 * skill written differently across postings still resolves to one record. [proficiency] is the
 * user's own assessment and is kept apart from evidence-based judgement (domain §4.3).
 */
data class Skill(
    val id: SkillId,
    val workspaceId: WorkspaceId,
    val canonicalName: String,
    val category: SkillCategory,
    val aliases: List<String> = emptyList(),
    val proficiency: ProficiencyLevel? = null,
    val lastUsedAt: LocalDate? = null,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(canonicalName.isNotBlank()) { "skill name must not be blank" }
        domainRequire(canonicalName.length <= MAX_NAME_LENGTH) { "skill name must be at most $MAX_NAME_LENGTH characters" }
        domainRequire(aliases.size <= MAX_ALIASES) { "a skill may carry at most $MAX_ALIASES aliases" }
        aliases.forEach {
            domainRequire(it.isNotBlank()) { "skill alias must not be blank" }
            domainRequire(it.length <= MAX_NAME_LENGTH) { "skill alias must be at most $MAX_NAME_LENGTH characters" }
        }
        val names = (aliases + canonicalName).map(::normalizeName)
        domainRequire(names.size == names.toSet().size) { "skill aliases must differ from each other and from the name" }
    }

    /** Every spelling this skill answers to, for duplicate detection and lookup. */
    val allNames: Set<String> get() = (listOf(canonicalName) + aliases).map(::normalizeName).toSet()

    /** True when [text] is this skill under any of its spellings. */
    fun isKnownAs(text: String): Boolean = normalizeName(text) in allNames

    /** The user changed the record; identity and workspace stay. */
    fun revisedTo(next: Skill): Skill {
        domainRequire(next.id == id && next.workspaceId == workspaceId) { "skill identity must not change" }
        return next.copy(revision = revision.next())
    }

    companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_ALIASES = 20

        /** Case- and spacing-insensitive form; two skills with the same normalized name are one skill. */
        fun normalizeName(text: String): String = text.trim().lowercase().replace(WHITESPACE, " ")

        private val WHITESPACE = Regex("\\s+")
    }
}
