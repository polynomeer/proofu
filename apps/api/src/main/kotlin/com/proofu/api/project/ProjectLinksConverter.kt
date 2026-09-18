package com.proofu.api.project

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/** Wire/storage shape of a project link; the domain [com.proofu.domain.career.ProjectLink] validates it. */
data class ProjectLinkJson(
    val label: String,
    val url: String,
)

/** Serialises the links list into the jsonb column (paired with @JdbcTypeCode(SqlTypes.JSON)). */
@Converter
class ProjectLinksConverter : AttributeConverter<List<ProjectLinkJson>, String> {
    override fun convertToDatabaseColumn(attribute: List<ProjectLinkJson>?): String =
        MAPPER.writeValueAsString(attribute ?: emptyList<ProjectLinkJson>())

    override fun convertToEntityAttribute(dbData: String?): List<ProjectLinkJson> =
        if (dbData.isNullOrBlank()) emptyList() else MAPPER.readValue(dbData, LIST_TYPE)

    private companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
        val LIST_TYPE = MAPPER.typeFactory.constructCollectionType(List::class.java, ProjectLinkJson::class.java)
    }
}
