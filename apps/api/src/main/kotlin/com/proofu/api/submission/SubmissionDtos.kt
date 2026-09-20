package com.proofu.api.submission

import com.proofu.api.document.DocumentVersionResponse
import com.proofu.api.document.TemplateSectionResponse
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.VersionAuthor
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateSubmissionRequest(
    @field:NotNull val documentVersionId: UUID?,
    val submittedAt: Instant? = null,
    @field:Size(max = 500) val note: String? = null,
)

data class SubmissionResponse(
    val id: UUID,
    val applicationId: UUID,
    val documentId: UUID,
    val documentTitle: String,
    val documentType: DocumentType,
    val documentVersionId: UUID,
    val versionLabel: String?,
    val versionCreatedBy: VersionAuthor,
    val postingSnapshotId: UUID,
    val hash: String,
    val submittedAt: Instant,
    val createdAt: Instant,
)

data class SubmissionDetailResponse(
    val id: UUID,
    val applicationId: UUID,
    val documentId: UUID,
    val documentTitle: String,
    val documentType: DocumentType,
    val documentVersionId: UUID,
    val versionLabel: String?,
    val versionCreatedBy: VersionAuthor,
    val postingSnapshotId: UUID,
    val hash: String,
    val submittedAt: Instant,
    val createdAt: Instant,
    val version: DocumentVersionResponse,
    val sections: List<TemplateSectionResponse>,
    val company: String,
    val roleTitle: String,
)

data class SubmissionList(
    val items: List<SubmissionResponse>,
)
