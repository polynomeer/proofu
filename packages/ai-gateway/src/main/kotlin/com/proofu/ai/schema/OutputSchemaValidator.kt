package com.proofu.ai.schema

import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

/** Grounding policy step 6: model output is validated against the caller's JSON Schema before anything reads it. */
class OutputSchemaValidator {
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
    private val compiled = ConcurrentHashMap<String, Schema>()

    /** Returns the parsed JSON when valid; a list of human-readable violations otherwise. */
    fun validate(
        schemaJson: String,
        outputText: String,
    ): Result {
        val node =
            try {
                MAPPER.readTree(outputText)
            } catch (e: Exception) {
                return Result.Invalid(listOf("output is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}"))
            }
        val schema = compiled.computeIfAbsent(schemaJson) { registry.getSchema(it) }
        val problems = schema.validate(node).map { it.message }
        return if (problems.isEmpty()) Result.Valid(node) else Result.Invalid(problems)
    }

    sealed interface Result {
        data class Valid(
            val json: JsonNode,
        ) : Result

        data class Invalid(
            val problems: List<String>,
        ) : Result
    }

    companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().build()
    }
}
