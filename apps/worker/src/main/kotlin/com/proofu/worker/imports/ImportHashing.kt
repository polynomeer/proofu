package com.proofu.worker.imports

import com.proofu.domain.imports.ImportItem
import com.proofu.domain.imports.ImportItemType
import com.proofu.worker.jobs.JobFailure
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Reads a bundle out of a job payload, the same way for planning and for applying.
 *
 * Both jobs receive the bundle in their payload — nothing of it is stored in between — so they
 * have to agree on what an item is and what it hashes to. One place, used twice.
 */
@Component
class ImportHashing {
    fun itemsOf(payload: JsonNode): List<ImportItem> {
        val contents =
            payload
                .get("contents")
                ?.properties()
                ?.associate { it.key to it.value.asString() }
                .orEmpty()
        return payload.get("items")?.toList()?.map { it.toItem(contents) } ?: emptyList()
    }

    private fun JsonNode.toItem(contents: Map<String, String>): ImportItem {
        val key = get("sourceKey")?.asString().orEmpty()
        val type =
            runCatching { ImportItemType.valueOf(get("type")?.asString().orEmpty()) }
                .getOrElse { throw JobFailure("VALIDATION_FAILED", "unknown item type", retryable = false) }
        val fields = get("fields")?.properties()?.associate { it.key to it.value.asString() }.orEmpty()
        val links =
            get("links")
                ?.properties()
                ?.associate { (name, node) -> name to node.toList().map { it.asString() } }
                .orEmpty()
        val content = contents[key].orEmpty()
        return ImportItem(
            sourceKey = key,
            type = type,
            contentHash = ImportItem.canonicalHash(type, fields, content),
            sourcePath = get("sourcePath")?.asString(),
            fields = fields,
            links = links,
            content = content,
        )
    }
}
