package com.proofu.api.document

import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.DiffKind
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.ProvenanceRelation
import com.proofu.domain.documents.ProvenanceSourceType
import com.proofu.domain.documents.RevisionMode
import com.proofu.domain.documents.SegmentKind
import com.proofu.domain.documents.TemplateSection
import com.proofu.domain.documents.VersionAuthor
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateDocumentRequest(
    @field:NotNull val type: DocumentType?,
    @field:NotBlank @field:Size(max = 200) val title: String?,
    @field:Size(max = 10) val language: String? = null,
)

/** Wire shape of a block; also the shape stored in document_versions.content_json. */
data class DocumentBlockDto(
    @field:NotBlank val blockId: String?,
    @field:NotNull val text: String?,
    val claimRefs: List<UUID> = emptyList(),
    val evidenceRefs: List<UUID> = emptyList(),
    val requirementRefs: List<UUID> = emptyList(),
    @field:NotNull val certainty: Certainty?,
    val warnings: List<String> = emptyList(),
    val approvedByUser: Boolean = false,
) {
    fun toDomain() =
        GeneratedBlock(
            blockId = requireNotNull(blockId).trim(),
            text = requireNotNull(text).trim(),
            claimRefs = claimRefs.map(::ClaimId).toSet(),
            evidenceRefs = evidenceRefs.map(::EvidenceId).toSet(),
            requirementRefs = requirementRefs.map(::RequirementId).toSet(),
            certainty = requireNotNull(certainty),
            warnings = warnings,
            approvedByUser = approvedByUser,
        )

    companion object {
        fun from(b: GeneratedBlock) =
            DocumentBlockDto(
                blockId = b.blockId,
                text = b.text,
                claimRefs = b.claimRefs.map { it.value },
                evidenceRefs = b.evidenceRefs.map { it.value },
                requirementRefs = b.requirementRefs.map { it.value },
                certainty = b.certainty,
                warnings = b.warnings,
                approvedByUser = b.approvedByUser,
            )
    }
}

data class CreateVersionRequest(
    /** Must be the document's latest version; null only while the document has none. */
    val parentVersionId: UUID? = null,
    @field:Size(max = 120) val label: String? = null,
    @field:NotNull @field:Valid val blocks: List<DocumentBlockDto>?,
)

data class GenerationRequest(
    val templateVersion: String? = null,
    val parentVersionId: UUID? = null,
    val claimIds: List<UUID>? = null,
    val requirementIds: List<UUID>? = null,
)

data class RevisionRequest(
    @field:NotNull val versionId: UUID?,
    @field:NotBlank val blockId: String?,
    @field:NotNull val mode: RevisionMode?,
)

data class ProvenanceLinkResponse(
    val blockId: String,
    val sourceType: ProvenanceSourceType,
    val sourceId: UUID,
    val sourceRevision: Long,
    val relation: ProvenanceRelation,
)

data class DocumentVersionResponse(
    val id: UUID,
    val documentId: UUID,
    val parentId: UUID?,
    val label: String?,
    val blocks: List<DocumentBlockDto>,
    val templateVersion: String,
    val promptVersion: String?,
    val modelRef: String?,
    val createdBy: VersionAuthor,
    val createdAt: Instant,
    val provenance: List<ProvenanceLinkResponse>,
)

data class TemplateSectionResponse(
    val id: String,
    val title: String,
    val guidance: String,
) {
    companion object {
        fun from(s: TemplateSection) = TemplateSectionResponse(s.id, s.title, s.guidance)
    }
}

data class DocumentResponse(
    val id: UUID,
    val applicationId: UUID,
    val type: DocumentType,
    val title: String,
    val language: String,
    val latestVersionId: UUID?,
    val latestVersionCreatedBy: VersionAuthor?,
    val company: String? = null,
    val roleTitle: String? = null,
    val applicationStatus: ApplicationStatus? = null,
    val submissionCount: Int? = null,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DocumentPage(
    val items: List<DocumentResponse>,
    val nextCursor: String?,
)

data class DocumentDetailResponse(
    val id: UUID,
    val applicationId: UUID,
    val type: DocumentType,
    val title: String,
    val language: String,
    val latestVersionId: UUID?,
    val latestVersionCreatedBy: VersionAuthor?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val latestVersion: DocumentVersionResponse?,
    val sections: List<TemplateSectionResponse>,
    val pendingApprovalCount: Int,
)

data class DocumentList(
    val items: List<DocumentResponse>,
)

data class DocumentVersionList(
    val items: List<DocumentVersionResponse>,
)

data class DiffSegmentResponse(
    val kind: SegmentKind,
    val text: String,
)

data class BlockDiffResponse(
    val blockId: String,
    val kind: DiffKind,
    val changedFields: List<String>,
    val base: DocumentBlockDto?,
    val target: DocumentBlockDto?,
    val textDiff: List<DiffSegmentResponse>,
)

data class VersionDiffResponse(
    val baseVersionId: UUID,
    val targetVersionId: UUID,
    val added: Int,
    val removed: Int,
    val changed: Int,
    val unchanged: Int,
    val entries: List<BlockDiffResponse>,
)
