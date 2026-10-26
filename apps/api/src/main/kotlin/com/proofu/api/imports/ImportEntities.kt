package com.proofu.api.imports

import com.proofu.domain.imports.ImportItemStatus
import com.proofu.domain.imports.ImportItemType
import com.proofu.domain.imports.ImportRunStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

/** `import_runs` (V21): one bundle that was planned. The bundle itself is never stored. */
@Entity
@Table(name = "import_runs")
class ImportRunEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "bundle_hash")
    val bundleHash: String,
    val source: String?,
    @Enumerated(EnumType.STRING)
    var status: ImportRunStatus,
    @Column(name = "item_count")
    var itemCount: Int,
    @Column(name = "applied_at")
    var appliedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
}

/** `import_items` (V21): one verdict per key per run, and what it became once applied. */
@Entity
@Table(name = "import_items")
class ImportItemEntity(
    @Id
    val id: UUID,
    @Column(name = "run_id")
    val runId: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "source_key")
    val sourceKey: String,
    @Column(name = "source_path")
    val sourcePath: String?,
    @Enumerated(EnumType.STRING)
    val type: ImportItemType,
    @Column(name = "content_hash")
    val contentHash: String,
    @Enumerated(EnumType.STRING)
    var status: ImportItemStatus,
    var reason: String?,
    @Column(name = "target_type")
    var targetType: String? = null,
    @Column(name = "target_id")
    var targetId: UUID? = null,
    @Column(name = "applied_at")
    var appliedAt: Instant? = null,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
}

interface ImportRunRepository : JpaRepository<ImportRunEntity, UUID> {
    fun findByIdAndWorkspaceId(
        id: UUID,
        workspaceId: UUID,
    ): ImportRunEntity?

    /** Keyset page over (created_at desc, id desc), matching the index V21 put there. */
    @Query(
        nativeQuery = true,
        value = """
            select * from import_runs
            where workspace_id = :workspaceId
              and (cast(:cursorAt as timestamptz) is null
                   or (created_at, id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by created_at desc, id desc
            limit :limit
            """,
    )
    fun page(
        workspaceId: UUID,
        cursorAt: Instant?,
        cursorId: UUID?,
        limit: Int,
    ): List<ImportRunEntity>
}

interface ImportItemRepository : JpaRepository<ImportItemEntity, UUID> {
    fun findByRunIdOrderByCreatedAtAsc(runId: UUID): List<ImportItemEntity>
}
