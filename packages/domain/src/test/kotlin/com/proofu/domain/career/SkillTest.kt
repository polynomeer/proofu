package com.proofu.domain.career

import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.Revision
import com.proofu.domain.common.SkillId
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SkillTest {
    private val ws = WorkspaceId(Fixtures.uuid(1))
    private val id = SkillId(Fixtures.uuid(2))

    private fun skill(
        name: String = "Kotlin",
        aliases: List<String> = emptyList(),
    ) = Skill(id, ws, name, SkillCategory.PROGRAMMING_LANGUAGE, aliases)

    @Test
    fun `a skill answers to its name and every alias, ignoring case and spacing`() {
        val s = skill("Spring Boot", listOf("SpringBoot", "스프링 부트"))
        assertThat(s.isKnownAs("spring   boot")).isTrue()
        assertThat(s.isKnownAs(" springboot ")).isTrue()
        assertThat(s.isKnownAs("스프링 부트")).isTrue()
        assertThat(s.isKnownAs("Spring")).isFalse()
        assertThat(s.allNames).containsExactlyInAnyOrder("spring boot", "springboot", "스프링 부트")
    }

    @Test
    fun `aliases may not repeat the name or each other`() {
        assertThatThrownBy { skill("Kotlin", listOf("kotlin")) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { skill("Kotlin", listOf("KT", "kt")) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { skill("Kotlin", listOf(" ")) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { skill("Kotlin", List(Skill.MAX_ALIASES + 1) { "a$it" }) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `name is required and bounded`() {
        assertThatThrownBy { skill("  ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { skill("k".repeat(Skill.MAX_NAME_LENGTH + 1)) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThat(skill("k".repeat(Skill.MAX_NAME_LENGTH)).canonicalName).hasSize(Skill.MAX_NAME_LENGTH)
    }

    @Test
    fun `proficiency is the user's own assessment and last use is a plain date`() {
        val s =
            skill().copy(proficiency = ProficiencyLevel.ADVANCED, lastUsedAt = LocalDate.of(2026, 3, 1))
        assertThat(s.proficiency?.rank).isEqualTo(4)
        assertThat(s.lastUsedAt).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(skill().proficiency).isNull()
    }

    @Test
    fun `a revision keeps identity and advances the revision`() {
        val first = skill()
        val next = first.revisedTo(first.copy(canonicalName = "Kotlin/JVM"))
        assertThat(next.revision).isEqualTo(Revision.INITIAL.next())
        assertThat(next.canonicalName).isEqualTo("Kotlin/JVM")
        assertThatThrownBy { first.revisedTo(first.copy(id = SkillId(Fixtures.uuid(9)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }
}
