package com.proofu.api

import com.proofu.api.identity.HeaderWorkspaceResolver
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Helpers shared by API integration tests: workspace seeding and JSON round trips. */
class ApiTestSupport(
    private val client: RestTestClient,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun newWorkspace(): UUID {
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            userId.toString(),
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", id, userId)
        return id
    }

    /** POSTs JSON, expects 201 and returns the created id. */
    fun create(
        workspace: UUID,
        path: String,
        json: String,
    ): UUID {
        val body =
            client
                .post()
                .uri(path)
                .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        return UUID.fromString(mapper.readTree(body).get("id").asString())
    }

    /** GETs JSON, expects 200 and returns the parsed object. */
    @Suppress("UNCHECKED_CAST")
    fun getJson(
        workspace: UUID,
        path: String,
    ): Map<String, Any?> {
        val body =
            client
                .get()
                .uri(path)
                .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        return mapper.readValue(body, Map::class.java) as Map<String, Any?>
    }

    fun titles(
        page: Map<String, Any?>,
        key: String = "title",
    ): List<Any?> =
        (page["items"] as List<*>).map {
            (it as Map<*, *>)[key]
        }
}
