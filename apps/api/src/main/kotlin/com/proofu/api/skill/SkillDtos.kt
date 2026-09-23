package com.proofu.api.skill

import com.proofu.domain.career.ProficiencyLevel
import com.proofu.domain.career.Skill
import com.proofu.domain.career.SkillCategory
import com.proofu.domain.common.Revision
import com.proofu.domain.common.SkillId
import com.proofu.domain.common.WorkspaceId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Matches `SkillInput` in the contract. */
data class SkillRequest(
    @field:NotBlank
    @field:Size(max = Skill.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
    val canonicalName: String?,
    @field:NotNull val category: SkillCategory?,
    @field:Size(max = Skill.MAX_ALIASES)
    val aliases: List<
        @NotBlank
        @Size(max = Skill.MAX_NAME_LENGTH, message = "{max}자 이하로 입력하세요")
        String,
    > =
        emptyList(),
    val proficiency: ProficiencyLevel? = null,
    val lastUsedAt: LocalDate? = null,
    val revision: Long? = null,
) {
    fun toDomain(
        id: SkillId,
        workspaceId: WorkspaceId,
        revision: Revision,
    ): Skill =
        Skill(
            id = id,
            workspaceId = workspaceId,
            canonicalName = requireNotNull(canonicalName).trim(),
            category = requireNotNull(category),
            aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() },
            proficiency = proficiency,
            lastUsedAt = lastUsedAt,
            revision = revision,
        )
}

data class SkillResponse(
    val id: UUID,
    val canonicalName: String,
    val category: SkillCategory,
    val aliases: List<String>,
    val proficiency: ProficiencyLevel?,
    val lastUsedAt: LocalDate?,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: SkillEntity) =
            SkillResponse(
                id = e.id,
                canonicalName = e.canonicalName,
                category = e.category,
                aliases = e.aliases.toList(),
                proficiency = e.proficiency,
                lastUsedAt = e.lastUsedAt,
                revision = e.revision,
                createdAt = checkNotNull(e.createdAt),
                updatedAt = checkNotNull(e.updatedAt),
            )
    }
}

data class SkillPage(
    val items: List<SkillResponse>,
    val nextCursor: String?,
)

data class SkillList(
    val items: List<SkillResponse>,
)

data class ProjectSkillsRequest(
    @field:NotNull @field:Size(max = MAX_PROJECT_SKILLS) val skillIds: List<UUID>?,
)

const val MAX_PROJECT_SKILLS = 50
