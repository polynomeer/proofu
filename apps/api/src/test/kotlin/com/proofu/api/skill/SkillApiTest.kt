package com.proofu.api.skill

import com.proofu.api.ApiTestSupport
import com.proofu.api.TestcontainersConfiguration
import com.proofu.api.identity.HeaderWorkspaceResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class SkillApiTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    private lateinit var support: ApiTestSupport
    private lateinit var workspaceId: UUID

    @BeforeEach
    fun seed() {
        support = ApiTestSupport(client, jdbc, mapper)
        workspaceId = support.newWorkspace()
    }

    private fun post(
        workspace: UUID,
        path: String,
        json: String,
    ) = client
        .post()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspace.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    private fun patch(
        path: String,
        json: String,
    ) = client
        .patch()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    private fun put(
        path: String,
        json: String,
    ) = client
        .put()
        .uri(path)
        .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange()

    @Test
    fun `a skill keeps its aliases and self-assessed level`() {
        val id =
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"Spring Boot","category":"FRAMEWORK","aliases":["SpringBoot","스프링 부트"],
                    "proficiency":"ADVANCED","lastUsedAt":"2026-06-01"}""",
            )
        val skill = support.getJson(workspaceId, "/api/v1/skills/$id")
        assertThat(skill["canonicalName"]).isEqualTo("Spring Boot")
        assertThat(skill["aliases"] as List<*>).containsExactly("SpringBoot", "스프링 부트")
        assertThat(skill["proficiency"]).isEqualTo("ADVANCED")
        assertThat(skill["lastUsedAt"]).isEqualTo("2026-06-01")
        assertThat(skill["revision"]).isEqualTo(1)

        val updated =
            patch("/api/v1/skills/$id", """{"canonicalName":"Spring Boot","category":"FRAMEWORK","revision":1}""")
                .expectStatus()
                .isOk
                .expectBody(Map::class.java)
                .returnResult()
                .responseBody!!
        assertThat(updated["aliases"] as List<*>).isEmpty()
        assertThat(updated["revision"]).isEqualTo(2)
        // A stale revision loses.
        patch("/api/v1/skills/$id", """{"canonicalName":"Spring","category":"FRAMEWORK","revision":1}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")
    }

    @Test
    fun `a name or alias another skill already answers to is refused, per workspace`() {
        support.create(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE"}""",
        )
        // Same name in different spelling.
        post(workspaceId, "/api/v1/skills", """{"canonicalName":" kotlin ","category":"PROGRAMMING_LANGUAGE"}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_DUPLICATE")
        // An alias that collides with an existing name.
        post(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"KT","category":"PROGRAMMING_LANGUAGE","aliases":["KOTLIN"]}""",
        ).expectStatus().isEqualTo(409)
        // Aliases repeating each other never reach the database.
        post(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"Rust","category":"PROGRAMMING_LANGUAGE","aliases":["rs","RS"]}""",
        ).expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
        // Another workspace is free to use the same name.
        val other = support.newWorkspace()
        post(other, "/api/v1/skills", """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE"}""")
            .expectStatus()
            .isCreated
    }

    @Test
    fun `the list filters by category and pages newest first`() {
        support.create(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE"}""",
        )
        support.create(workspaceId, "/api/v1/skills", """{"canonicalName":"Postgres","category":"DATA"}""")
        support.create(workspaceId, "/api/v1/skills", """{"canonicalName":"Figma","category":"TOOL"}""")

        val all = support.getJson(workspaceId, "/api/v1/skills")
        assertThat(support.titles(all, "canonicalName")).hasSize(3)
        val data = support.getJson(workspaceId, "/api/v1/skills?category=DATA")
        assertThat(support.titles(data, "canonicalName")).containsExactly("Postgres")

        val firstPage = support.getJson(workspaceId, "/api/v1/skills?limit=2")
        assertThat(support.titles(firstPage, "canonicalName")).hasSize(2)
        val cursor = firstPage["nextCursor"] as String?
        assertThat(cursor).isNotNull()
        val second = support.getJson(workspaceId, "/api/v1/skills?limit=2&cursor=$cursor")
        assertThat(support.titles(second, "canonicalName")).hasSize(1)
        assertThat(second["nextCursor"]).isNull()
    }

    @Test
    fun `project links are replaced as a set and drop when the skill goes to the trash`() {
        val entry =
            support.create(
                workspaceId,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2020-01-01"}""",
            )
        val project =
            support.create(
                workspaceId,
                "/api/v1/projects",
                """{"careerEntryId":"$entry","name":"Checkout","role":"Lead","summary":"Rebuilt checkout"}""",
            )
        val kotlin =
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE"}""",
            )
        val postgres =
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"Postgres","category":"DATA"}""",
            )

        put("/api/v1/projects/$project/skills", """{"skillIds":["$kotlin","$postgres","$kotlin"]}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(2)
        // Replacing the set removes what is no longer in it.
        put("/api/v1/projects/$project/skills", """{"skillIds":["$postgres"]}""").expectStatus().isOk
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/projects/$project/skills"), "canonicalName"))
            .containsExactly("Postgres")

        // A skill of another workspace is "not found", not forbidden.
        val other = support.newWorkspace()
        val foreign =
            support.create(
                other,
                "/api/v1/skills",
                """{"canonicalName":"Swift","category":"PROGRAMMING_LANGUAGE"}""",
            )
        put("/api/v1/projects/$project/skills", """{"skillIds":["$foreign"]}""").expectStatus().isNotFound
        // The failed call changed nothing.
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/projects/$project/skills"), "canonicalName"))
            .containsExactly("Postgres")

        client
            .delete()
            .uri("/api/v1/skills/$postgres")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/projects/$project/skills"), "canonicalName"))
            .isEmpty()
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/skills"), "canonicalName"))
            .containsExactly("Kotlin")
        // The deleted name is free again.
        post(workspaceId, "/api/v1/skills", """{"canonicalName":"Postgres","category":"DATA"}""")
            .expectStatus()
            .isCreated
    }
}
