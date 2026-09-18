package com.proofu.api.achievement

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AchievementRepository : JpaRepository<AchievementEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): AchievementEntity?

    fun findAllByProjectIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        projectId: UUID,
        workspaceId: UUID,
    ): List<AchievementEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select a from AchievementEntity a where a.id = :id and a.workspaceId = :workspaceId and a.deletedAt is null",
    )
    fun lockByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): AchievementEntity?
}
