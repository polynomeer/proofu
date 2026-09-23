package com.proofu.api.skill

import com.proofu.domain.career.ProficiencyLevel
import com.proofu.domain.career.Skill
import com.proofu.domain.career.SkillCategory
import com.proofu.domain.common.Revision
import com.proofu.domain.common.SkillId
import com.proofu.domain.common.WorkspaceId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "skills")
class SkillEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "canonical_name")
    var canonicalName: String,
    @Enumerated(EnumType.STRING)
    var category: SkillCategory,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var aliases: Array<String>,
    @Enumerated(EnumType.STRING)
    var proficiency: ProficiencyLevel?,
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDate?,
    var revision: Long,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    fun apply(skill: Skill) {
        require(skill.id.value == id && skill.workspaceId.value == workspaceId) { "entity identity mismatch" }
        canonicalName = skill.canonicalName
        category = skill.category
        aliases = skill.aliases.toTypedArray()
        proficiency = skill.proficiency
        lastUsedAt = skill.lastUsedAt
        revision = skill.revision.value
    }

    fun toDomain(): Skill =
        Skill(
            id = SkillId(id),
            workspaceId = WorkspaceId(workspaceId),
            canonicalName = canonicalName,
            category = category,
            aliases = aliases.toList(),
            proficiency = proficiency,
            lastUsedAt = lastUsedAt,
            revision = Revision(revision),
        )

    companion object {
        fun from(skill: Skill): SkillEntity =
            SkillEntity(
                id = skill.id.value,
                workspaceId = skill.workspaceId.value,
                canonicalName = skill.canonicalName,
                category = skill.category,
                aliases = skill.aliases.toTypedArray(),
                proficiency = skill.proficiency,
                lastUsedAt = skill.lastUsedAt,
                revision = skill.revision.value,
            )
    }
}
