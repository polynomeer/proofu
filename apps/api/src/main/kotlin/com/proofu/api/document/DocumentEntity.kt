package com.proofu.api.document

import com.proofu.domain.documents.DocumentType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/** Mutable identity of a document; its content lives in append-only document_versions. */
@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    val id: UUID,
    @Column(name = "workspace_id")
    val workspaceId: UUID,
    @Column(name = "application_id")
    val applicationId: UUID,
    @Enumerated(EnumType.STRING)
    val type: DocumentType,
    var title: String,
    val language: String,
    /** Bumped on every new version so listing clients can detect change cheaply. */
    var version: Long,
) {
    @Generated(event = [EventType.INSERT])
    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
