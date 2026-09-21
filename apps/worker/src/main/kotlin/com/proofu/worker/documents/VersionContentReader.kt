package com.proofu.worker.documents

import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.RequirementId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Reads document_versions.content_json (the API's DocumentBlock shape) back into the domain model. */
@Component
class VersionContentReader(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun output(versionId: UUID): GeneratedOutput {
        val json =
            jdbc.queryForObject(
                "select content_json::text from document_versions where id = ?",
                String::class.java,
                versionId,
            )
                ?: return GeneratedOutput(emptyList())
        val blocks = mutableListOf<GeneratedBlock>()
        for (node in mapper.readTree(json).get("blocks") ?: return GeneratedOutput(emptyList())) {
            blocks +=
                GeneratedBlock(
                    blockId = node.get("blockId").asString(),
                    text = node.get("text").asString(),
                    claimRefs = node.uuids("claimRefs").map(::ClaimId).toSet(),
                    evidenceRefs = node.uuids("evidenceRefs").map(::EvidenceId).toSet(),
                    requirementRefs = node.uuids("requirementRefs").map(::RequirementId).toSet(),
                    certainty = Certainty.valueOf(node.get("certainty").asString()),
                    warnings =
                        node.get("warnings")?.let { w -> buildList { for (x in w) add(x.asString()) } } ?: emptyList(),
                    approvedByUser = node.get("approvedByUser")?.asBoolean() ?: false,
                )
        }
        return GeneratedOutput(blocks)
    }

    private fun JsonNode.uuids(field: String): List<UUID> {
        val node = get(field) ?: return emptyList()
        return buildList { for (item in node) add(UUID.fromString(item.asString())) }
    }
}
