package com.proofu.domain.documents

import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.Revision
import java.util.UUID

enum class ProvenanceSourceType {
    CAREER_ENTRY,
    PROJECT,
    ACHIEVEMENT,
    CLAIM,
    EVIDENCE,
    REQUIREMENT,
}

enum class ProvenanceRelation {
    DERIVED_FROM,
    CITES,
    ADDRESSES,
}

/** Pins a block of a document version to a specific source revision. Never updated. */
data class ProvenanceLink(
    val versionId: DocumentVersionId,
    val blockId: String,
    val sourceType: ProvenanceSourceType,
    val sourceId: UUID,
    val sourceRevision: Revision,
    val relation: ProvenanceRelation,
) {
    init {
        require(blockId.isNotBlank()) { "provenance block id must not be blank" }
    }
}
