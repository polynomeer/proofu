package com.proofu.api.document

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface DocumentRepository : JpaRepository<DocumentEntity, UUID> {
    fun findByIdAndWorkspaceIdAndDeletedAtIsNull(
        id: UUID,
        workspaceId: UUID,
    ): DocumentEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentEntity d where d.id = :id and d.workspaceId = :workspaceId and d.deletedAt is null")
    fun lockByIdInWorkspace(
        id: UUID,
        workspaceId: UUID,
    ): DocumentEntity?

    fun findAllByApplicationIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        applicationId: UUID,
        workspaceId: UUID,
    ): List<DocumentEntity>
}
