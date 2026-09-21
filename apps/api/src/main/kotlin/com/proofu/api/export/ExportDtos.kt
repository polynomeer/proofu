package com.proofu.api.export

import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.ExportStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class StartExportRequest(
    @field:NotNull val format: ExportFormat?,
)

data class ExportAccepted(
    val jobId: UUID,
    val exportId: UUID,
)

data class ExportResponse(
    val id: UUID,
    val documentVersionId: UUID,
    val format: ExportFormat,
    val templateVersion: String,
    val rendererVersion: String?,
    val status: ExportStatus,
    val errorCode: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val pageCount: Int?,
    val mimeType: String?,
    val fileName: String,
    val jobId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ExportList(
    val items: List<ExportResponse>,
)

/** A READY file with what the download response needs. */
data class ExportFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
