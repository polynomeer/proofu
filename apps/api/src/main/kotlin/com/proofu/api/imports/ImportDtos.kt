package com.proofu.api.imports

import com.proofu.domain.imports.ImportItemStatus
import com.proofu.domain.imports.ImportItemType
import com.proofu.domain.imports.ImportRunStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * A bundle, as an adapter produces it (docs/product/personal-archive-import.md).
 *
 * There is no `contentHash` here on purpose: the server computes it over the canonical form of
 * each item, so "the same content" means the same thing to ProofU no matter how the adapter was
 * written. A hash the sender controls would break idempotency quietly.
 */
data class ImportBundleRequest(
    @field:Size(max = MAX_SOURCE_LENGTH) val source: String? = null,
    @field:NotNull
    @field:Size(min = 1, max = MAX_ITEMS, message = "한 번에 {max}건까지 보낼 수 있습니다")
    @field:Valid
    val items: List<ImportItemRequest>? = null,
    /** Item key → the text that belongs to it, such as a posting's raw text. */
    val contents: Map<
        String,
        @Size(max = MAX_CONTENT_LENGTH)
        String,
    > = emptyMap(),
)

data class ImportItemRequest(
    @field:NotBlank @field:Size(max = 200) val sourceKey: String? = null,
    @field:NotNull val type: ImportItemType? = null,
    @field:Size(max = 500) val sourcePath: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val links: Map<String, List<String>> = emptyMap(),
)

/**
 * What to create out of a plan, and the bundle to create it from.
 *
 * The bundle comes back with the request because none of it was stored: the ledger holds keys,
 * hashes and verdicts only. Each picked item is hashed again and must match what the plan
 * recorded, so a bundle that moved in between is refused rather than applied from a stale preview.
 */
data class ImportApplyRequest(
    @field:NotNull
    @field:Size(min = 1, max = MAX_ITEMS, message = "한 번에 {max}건까지 적용할 수 있습니다")
    val sourceKeys: List<String>? = null,
    @field:NotNull @field:Valid val bundle: ImportBundleRequest? = null,
)

data class ImportItemResponse(
    val id: UUID,
    val sourceKey: String,
    val sourcePath: String?,
    val type: ImportItemType,
    val status: ImportItemStatus,
    val reason: String?,
    val targetType: String?,
    val targetId: UUID?,
    val appliedAt: Instant?,
)

data class ImportRunResponse(
    val id: UUID,
    val source: String?,
    val status: ImportRunStatus,
    val itemCount: Int,
    /** How many items reached each verdict, so the screen can open on the total. */
    val summary: Map<ImportItemStatus, Int>,
    val items: List<ImportItemResponse>,
    val createdAt: Instant,
    val appliedAt: Instant?,
)

data class ImportRunSummary(
    val id: UUID,
    val source: String?,
    val status: ImportRunStatus,
    val itemCount: Int,
    val createdAt: Instant,
    val appliedAt: Instant?,
)

data class ImportRunPage(
    val items: List<ImportRunSummary>,
    val nextCursor: String?,
)

const val MAX_ITEMS = 500
const val MAX_SOURCE_LENGTH = 120
const val MAX_CONTENT_LENGTH = 200_000
