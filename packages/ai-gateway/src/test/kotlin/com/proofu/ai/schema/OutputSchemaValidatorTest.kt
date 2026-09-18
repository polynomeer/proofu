package com.proofu.ai.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutputSchemaValidatorTest {
    private val validator = OutputSchemaValidator()
    private val schema =
        """
        {"type":"object","required":["items"],"additionalProperties":false,
         "properties":{"items":{"type":"array","items":{"type":"object","required":["text","sourceId"],
           "properties":{"text":{"type":"string"},"sourceId":{"type":"string"}}}}}}
        """.trimIndent()

    @Test
    fun `valid output is parsed`() {
        val result = validator.validate(schema, """{"items":[{"text":"Kotlin","sourceId":"s1"}]}""")
        assertThat(result).isInstanceOf(OutputSchemaValidator.Result.Valid::class.java)
        assertThat((result as OutputSchemaValidator.Result.Valid).json.get("items").size()).isEqualTo(1)
    }

    @Test
    fun `missing fields, extra fields and non-json are reported`() {
        assertThat(validator.validate(schema, """{"items":[{"text":"Kotlin"}]}"""))
            .isInstanceOf(OutputSchemaValidator.Result.Invalid::class.java)
        assertThat(
            (validator.validate(schema, """{"items":[],"extra":1}""") as OutputSchemaValidator.Result.Invalid).problems,
        ).anyMatch { it.contains("extra") }
        assertThat((validator.validate(schema, "not json") as OutputSchemaValidator.Result.Invalid).problems.single())
            .startsWith("output is not valid JSON")
    }
}
