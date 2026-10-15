package com.proofu.api.careerlog

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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
class CareerLogApiTest {
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

    private fun careerEntry(): UUID =
        support.create(
            workspaceId,
            "/api/v1/career-entries",
            """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2020-01-01"}""",
        )

    private fun project(entryId: UUID): UUID =
        support.create(
            workspaceId,
            "/api/v1/projects",
            """{"careerEntryId":"$entryId","name":"Checkout","role":"Lead","summary":"Rebuilt checkout"}""",
        )

    @Test
    fun `a log is confidential by default and keeps the records it belongs to`() {
        val entry = careerEntry()
        val projectId = project(entry)
        val id =
            support.create(
                workspaceId,
                "/api/v1/career-logs",
                """{"occurredAt":"2026-10-14","body":"결제 실패 알림을 큐로 옮겼다",
                    "careerEntryIds":["$entry"],"projectIds":["$projectId"]}""",
            )
        val log = support.getJson(workspaceId, "/api/v1/career-logs/$id")
        assertThat(log["sensitivity"]).isEqualTo("CONFIDENTIAL")
        assertThat(log["careerEntryIds"] as List<*>).containsExactly(entry.toString())
        assertThat(log["projectIds"] as List<*>).containsExactly(projectId.toString())
        assertThat(log["promotion"]).isNull()
        assertThat(log["revision"]).isEqualTo(1)
        // A log has no visibility: there is no audience to publish it to.
        assertThat(log).doesNotContainKey("visibility")
    }

    @Test
    fun `nothing is written ahead of time`() {
        val tomorrow = LocalDate.now().plusDays(1)
        post(workspaceId, "/api/v1/career-logs", """{"occurredAt":"$tomorrow","body":"내일 할 일"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")
        post(workspaceId, "/api/v1/career-logs", """{"occurredAt":"2026-10-14","body":"   "}""")
            .expectStatus()
            .isEqualTo(400)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `editing replaces the link set and loses against a stale revision`() {
        val entry = careerEntry()
        val projectId = project(entry)
        val id =
            support.create(
                workspaceId,
                "/api/v1/career-logs",
                """{"occurredAt":"2026-10-10","body":"처음 적은 내용","careerEntryIds":["$entry"]}""",
            )
        val updated =
            patch(
                "/api/v1/career-logs/$id",
                """{"occurredAt":"2026-10-10","body":"고쳐 적은 내용","projectIds":["$projectId"],"revision":1}""",
            ).expectStatus()
                .isOk
                .expectBody(Map::class.java)
                .returnResult()
                .responseBody!!
        assertThat(updated["body"]).isEqualTo("고쳐 적은 내용")
        assertThat(updated["careerEntryIds"] as List<*>).isEmpty()
        assertThat(updated["projectIds"] as List<*>).containsExactly(projectId.toString())
        assertThat(updated["revision"]).isEqualTo(2)

        patch("/api/v1/career-logs/$id", """{"occurredAt":"2026-10-10","body":"또 고침","revision":1}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CONFLICT_STALE_VERSION")
    }

    @Test
    fun `a link to another workspace's record is not found`() {
        val foreign = support.newWorkspace()
        val foreignEntry =
            support.create(
                foreign,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"Elsewhere","startDate":"2020-01-01"}""",
            )
        post(
            workspaceId,
            "/api/v1/career-logs",
            """{"occurredAt":"2026-10-14","body":"남의 기록에 연결","careerEntryIds":["$foreignEntry"]}""",
        ).expectStatus()
            .isEqualTo(404)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("NOT_FOUND")
    }

    @Test
    fun `promotion is recorded once and the achievement stands on its own`() {
        val entry = careerEntry()
        val projectId = project(entry)
        val achievementId =
            support.create(
                workspaceId,
                "/api/v1/projects/$projectId/achievements",
                """{"action":"큐로 옮김","outcome":"재시도가 붙었다","confidence":0.8}""",
            )
        val logId =
            support.create(
                workspaceId,
                "/api/v1/career-logs",
                """{"occurredAt":"2026-10-14","body":"결제 실패 알림을 큐로 옮겼다"}""",
            )
        val promoted =
            post(
                workspaceId,
                "/api/v1/career-logs/$logId/promotion",
                """{"targetType":"ACHIEVEMENT","targetId":"$achievementId"}""",
            ).expectStatus()
                .isOk
                .expectBody(Map::class.java)
                .returnResult()
                .responseBody!!
        val promotion = promoted["promotion"] as Map<*, *>
        assertThat(promotion["targetType"]).isEqualTo("ACHIEVEMENT")
        assertThat(promotion["targetId"]).isEqualTo(achievementId.toString())
        assertThat(promotion["promotedAt"]).isNotNull()

        // A second promotion would be a second copy of the same line.
        post(
            workspaceId,
            "/api/v1/career-logs/$logId/promotion",
            """{"targetType":"ACHIEVEMENT","targetId":"$achievementId"}""",
        ).expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("DOMAIN_RULE_VIOLATION")

        // The achievement is an ordinary achievement; nothing on it says it came from a log.
        val achievement = support.getJson(workspaceId, "/api/v1/achievements/$achievementId")
        assertThat(achievement.keys).noneMatch { it.toString().contains("log", ignoreCase = true) }
    }

    @Test
    fun `promoting to a record of another workspace is not found`() {
        val foreign = support.newWorkspace()
        val foreignProject =
            support.create(foreign, "/api/v1/projects", """{"name":"P","role":"r","summary":"s"}""")
        val foreignAchievement =
            support.create(
                foreign,
                "/api/v1/projects/$foreignProject/achievements",
                """{"action":"a","outcome":"o","confidence":0.5}""",
            )
        val logId =
            support.create(workspaceId, "/api/v1/career-logs", """{"occurredAt":"2026-10-14","body":"내 로그"}""")
        post(
            workspaceId,
            "/api/v1/career-logs/$logId/promotion",
            """{"targetType":"ACHIEVEMENT","targetId":"$foreignAchievement"}""",
        ).expectStatus()
            .isEqualTo(404)
    }

    @Test
    fun `the list pages by when it happened and can show only what is still waiting`() {
        val ids =
            (1..3).map { i ->
                support.create(
                    workspaceId,
                    "/api/v1/career-logs",
                    """{"occurredAt":"2026-10-0$i","body":"로그 $i"}""",
                )
            }
        val projectId = project(careerEntry())
        val achievementId =
            support.create(
                workspaceId,
                "/api/v1/projects/$projectId/achievements",
                """{"action":"a","outcome":"o","confidence":0.5}""",
            )
        post(
            workspaceId,
            "/api/v1/career-logs/${ids[2]}/promotion",
            """{"targetType":"ACHIEVEMENT","targetId":"$achievementId"}""",
        ).expectStatus().isOk

        val newestFirst = support.getJson(workspaceId, "/api/v1/career-logs")
        assertThat(support.titles(newestFirst, "body")).containsExactly("로그 3", "로그 2", "로그 1")

        val waiting = support.getJson(workspaceId, "/api/v1/career-logs?promoted=false")
        assertThat(support.titles(waiting, "body")).containsExactly("로그 2", "로그 1")

        val done = support.getJson(workspaceId, "/api/v1/career-logs?promoted=true")
        assertThat(support.titles(done, "body")).containsExactly("로그 3")

        // One page at a time, then the cursor.
        val firstPage = support.getJson(workspaceId, "/api/v1/career-logs?limit=2")
        assertThat(support.titles(firstPage, "body")).containsExactly("로그 3", "로그 2")
        val nextPage = support.getJson(workspaceId, "/api/v1/career-logs?limit=2&cursor=${firstPage["nextCursor"]}")
        assertThat(support.titles(nextPage, "body")).containsExactly("로그 1")
        assertThat(nextPage["nextCursor"]).isNull()
    }

    @Test
    fun `a deleted log leaves the list and another workspace never sees it`() {
        val id =
            support.create(workspaceId, "/api/v1/career-logs", """{"occurredAt":"2026-10-14","body":"지울 로그"}""")
        val other = support.newWorkspace()
        client
            .get()
            .uri("/api/v1/career-logs/$id")
            .header(HeaderWorkspaceResolver.HEADER, other.toString())
            .exchange()
            .expectStatus()
            .isEqualTo(404)

        client
            .delete()
            .uri("/api/v1/career-logs/$id")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .exchange()
            .expectStatus()
            .isNoContent
        assertThat(support.titles(support.getJson(workspaceId, "/api/v1/career-logs"), "body")).isEmpty()
        assertThat(
            jdbc.queryForObject("select count(*) from career_logs where id = ?", Long::class.java, id),
        ).isEqualTo(1)
    }
}
