package com.proofu.api.search

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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class SearchApiTest {
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

    @Suppress("UNCHECKED_CAST")
    private fun search(
        q: String,
        workspace: UUID = workspaceId,
    ): Map<String, Map<String, Any?>> {
        val body = support.getJson(workspace, "/api/v1/search?q=$q")
        return body.filterValues { it is Map<*, *> } as Map<String, Map<String, Any?>>
    }

    private fun titles(
        results: Map<String, Map<String, Any?>>,
        group: String,
    ): List<Any?> = (results.getValue(group)["items"] as List<*>).map { (it as Map<*, *>)["title"] }

    @Test
    fun `one query reaches every kind of record, scoped to the workspace`() {
        val entry =
            support.create(
                workspaceId,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"백엔드 엔지니어","organization":"결제주식회사","startDate":"2021-01-01"}""",
            )
        support.create(
            workspaceId,
            "/api/v1/projects",
            """{"careerEntryId":"$entry","name":"결제 재구축","role":"리드","summary":"파이프라인 재설계"}""",
        )
        support.create(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE","aliases":["결제 코틀린"]}""",
        )
        support.create(
            workspaceId,
            "/api/v1/evidence",
            """{"type":"NOTE","title":"결제 회고","source":"USER_INPUT","body":"장애 원인","capturedAt":"2026-01-01T00:00:00Z"}""",
        )
        client
            .post()
            .uri("/api/v1/job-postings/import")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body("""{"source":"MANUAL_TEXT","company":"결제주식회사","roleTitle":"서버 엔지니어","text":"요구사항"}""")
            .exchange()
            .expectStatus()
            .isCreated

        val hits = search("결제")
        assertThat(titles(hits, "careerEntries")).containsExactly("백엔드 엔지니어")
        assertThat(titles(hits, "projects")).containsExactly("결제 재구축")
        assertThat(titles(hits, "skills")).containsExactly("Kotlin") // matched through an alias
        assertThat(titles(hits, "evidence")).containsExactly("결제 회고")
        assertThat(titles(hits, "postings")).containsExactly("서버 엔지니어")
        val project = (hits.getValue("projects")["items"] as List<*>).first() as Map<*, *>
        assertThat(project["subtitle"]).isEqualTo("리드")
        assertThat(((hits.getValue("careerEntries")["items"] as List<*>).first() as Map<*, *>)["href"] as String)
            .isEqualTo("/career/$entry")

        // Another workspace sees nothing of it.
        val other = support.newWorkspace()
        val empty = search("결제", workspace = other)
        assertThat(empty.values.map { it["total"] }).allMatch { it == 0 }
    }

    @Test
    fun `a group reports how many it holds and returns at most the asked-for rows`() {
        repeat(7) {
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"검색 대상 $it","category":"TOOL"}""",
            )
        }
        val body = support.getJson(workspaceId, "/api/v1/search?q=검색 대상&limit=3")
        val skills = body["skills"] as Map<*, *>
        assertThat(skills["total"]).isEqualTo(7)
        assertThat(skills["items"] as List<*>).hasSize(3)
    }

    @Test
    fun `a query too short to be useful is refused, and a deleted record is gone from the results`() {
        val evidence =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"NOTE","title":"삭제될 메모","source":"USER_INPUT","body":"x","capturedAt":"2026-01-01T00:00:00Z"}""",
            )
        assertThat(titles(search("삭제될"), "evidence")).containsExactly("삭제될 메모")

        client
            .delete()
            .uri("/api/v1/evidence/$evidence")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(search("삭제될").getValue("evidence")["total"]).isEqualTo(0)

        client
            .get()
            .uri("/api/v1/search?q=a")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
