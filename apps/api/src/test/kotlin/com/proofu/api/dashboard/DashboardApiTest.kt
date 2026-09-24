package com.proofu.api.dashboard

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
class DashboardApiTest {
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
    private fun dashboard(query: String = "") = support.getJson(workspaceId, "/api/v1/dashboard$query")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.kpi(name: String) = (this["kpis"] as Map<String, Any?>)[name] as Map<String, Any?>

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `top skills are ordered by the record behind them, not by the level claimed`() {
        val empty = dashboard()["topSkills"] as Map<String, Any?>
        assertThat(empty["total"]).isEqualTo(0)
        assertThat(empty["items"] as List<*>).isEmpty()

        val entry =
            support.create(
                workspaceId,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"엔지니어","startDate":"2021-01-01"}""",
            )
        val used =
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"Kotlin","category":"PROGRAMMING_LANGUAGE","proficiency":"PRACTITIONER","lastUsedAt":"2026-01-01"}""",
            )
        // Claims the highest level but nothing uses it: the record, not the claim, decides order.
        support.create(
            workspaceId,
            "/api/v1/skills",
            """{"canonicalName":"COBOL","category":"PROGRAMMING_LANGUAGE","proficiency":"STRATEGIC"}""",
        )
        val recent =
            support.create(
                workspaceId,
                "/api/v1/skills",
                """{"canonicalName":"Figma","category":"TOOL","lastUsedAt":"2026-06-01"}""",
            )
        repeat(2) { i ->
            val project =
                support.create(
                    workspaceId,
                    "/api/v1/projects",
                    """{"careerEntryId":"$entry","name":"프로젝트 $i","role":"리드","summary":"요약"}""",
                )
            client
                .put()
                .uri("/api/v1/projects/$project/skills")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"skillIds":["$used"]}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        val top = dashboard()["topSkills"] as Map<String, Any?>
        assertThat(top["total"]).isEqualTo(3)
        val items = top["items"] as List<Map<String, Any?>>
        assertThat(items.map { it["canonicalName"] }).containsExactly("Kotlin", "Figma", "COBOL")
        assertThat(items.first()["projectCount"]).isEqualTo(2)
        assertThat(items.first()["proficiency"]).isEqualTo("PRACTITIONER")
        assertThat(items.last()["projectCount"]).isEqualTo(0)
        assertThat(items.map { it["id"] }).contains(used.toString(), recent.toString())
    }

    @Test
    fun `empty workspace reports zeros and asks for the first career entry`() {
        val d = dashboard()
        assertThat(d.kpi("careerEntries")["total"]).isEqualTo(0)
        assertThat(d.kpi("evidenceCoverage")["ratio"]).isEqualTo(0)
        assertThat(d["timeline"] as List<*>).isEmpty()
        assertThat((d["attention"] as List<*>).map { (it as Map<*, *>)["code"] }).containsExactly("NO_CAREER_ENTRIES")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `kpis, timeline counts and attention reflect the evidence chain`() {
        val entry =
            support.create(
                workspaceId,
                "/api/v1/career-entries",
                """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2022-01-01"}""",
            )
        support.create(
            workspaceId,
            "/api/v1/career-entries",
            """{"type":"CERTIFICATION","title":"PMP","startDate":"2019-08-01"}""",
        )
        val project =
            support.create(
                workspaceId,
                "/api/v1/projects",
                """{"careerEntryId":"$entry","name":"P","role":"r","summary":"s"}""",
            )
        val achievement =
            support.create(
                workspaceId,
                "/api/v1/projects/$project/achievements",
                """{"action":"a","outcome":"o","confidence":0.5}""",
            )
        val verified =
            support.create(
                workspaceId,
                "/api/v1/evidence",
                """{"type":"URL","title":"Dash","uri":"https://a","verification":"USER_VERIFIED"}""",
            )
        support.create(workspaceId, "/api/v1/evidence", """{"type":"NOTE","title":"Memo","body":"b"}""")
        val supported =
            support.create(
                workspaceId,
                "/api/v1/claims",
                """{"text":"c1","type":"FACT","sources":[{"type":"ACHIEVEMENT","id":"$achievement"}]}""",
            )
        support.create(
            workspaceId,
            "/api/v1/claims",
            """{"text":"c2","type":"FACT","sources":[{"type":"PROJECT","id":"$project"}]}""",
        )
        client
            .post()
            .uri("/api/v1/claims/$supported/evidence")
            .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"evidenceId":"$verified","relation":"SUPPORTS","confidence":0.9}""")
            .exchange()
            .expectStatus()
            .isCreated

        val d = dashboard()
        assertThat(d.kpi("careerEntries"))
            .containsEntry("total", 2)
            .containsEntry("addedLast30Days", 2)
            .containsEntry("projects", 1)
            .containsEntry("achievements", 1)
        assertThat(
            d.kpi("verifiedEvidence"),
        ).containsEntry("verified", 1).containsEntry("total", 2).containsEntry("expired", 0)
        assertThat(d.kpi("activeApplications")).containsEntry("total", 0)
        assertThat(
            d.kpi("evidenceCoverage"),
        ).containsEntry("claims", 2).containsEntry("supported", 1).containsEntry("ratio", 50)

        val timeline = d["timeline"] as List<Map<String, Any?>>
        assertThat(timeline.map { it["title"] }).containsExactly("Engineer", "PMP")
        assertThat(
            timeline[0],
        ).containsEntry("projectCount", 1).containsEntry("claimCount", 2).containsEntry("evidenceCount", 1)
        assertThat(timeline[1]).containsEntry("projectCount", 0).containsEntry("claimCount", 0)

        assertThat((d["recentEvidence"] as List<Map<String, Any?>>).map { it["title"] }).containsExactly("Memo", "Dash")
        assertThat((d["attention"] as List<Map<String, Any?>>).map { it["code"] to it["count"] })
            .containsExactly("UNSUPPORTED_CLAIMS" to 1, "UNVERIFIED_EVIDENCE" to 1)

        assertThat(
            (dashboard("?timelineType=CERTIFICATION")["timeline"] as List<Map<String, Any?>>).map { it["title"] },
        ).containsExactly("PMP")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `application flow items outrank evidence gaps and are capped at three`() {
        support.create(
            workspaceId,
            "/api/v1/career-entries",
            """{"type":"EMPLOYMENT","title":"E","startDate":"2022-01-01"}""",
        )
        val imported =
            client
                .post()
                .uri("/api/v1/job-postings/import")
                .header(HeaderWorkspaceResolver.HEADER, workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"source":"MANUAL_TEXT","company":"ABC","roleTitle":"PM","text":"SaaS 제품 기획 경험"}""")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
        val snapshotId = mapper.readTree(imported).get("snapshotId").asString()
        // Approved requirement + no match run → MATCH_NOT_RUN; a DRAFT requirement → DRAFT_REQUIREMENTS.
        support.create(
            workspaceId,
            "/api/v1/job-posting-snapshots/$snapshotId/requirements",
            """{"category":"REQUIRED","text":"SaaS 제품 기획 경험"}""",
        )
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, sort_order) values (?, ?::uuid, 'SKILL', 'Draft', 0.5, 'AI', 'DRAFT', 9)",
            UUID.randomUUID(),
            snapshotId,
        )
        val soon = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        jdbc.update("update applications set deadline_at = now() + interval '3 days' where id = ?", soon)
        val rejected = support.create(workspaceId, "/api/v1/applications", """{"snapshotId":"$snapshotId"}""")
        jdbc.update("update applications set status = 'DOCUMENT_REJECTED' where id = ?", rejected)

        val codes = (dashboard()["attention"] as List<Map<String, Any?>>).map { it["code"] to it["count"] }
        assertThat(codes).containsExactly("DEADLINES_SOON" to 1, "REVIEWS_PENDING" to 1, "DRAFT_REQUIREMENTS" to 1)

        // Once the drafts are gone and a match ran for the deadline one, the next items surface.
        jdbc.update("delete from requirements where status = 'DRAFT' and snapshot_id = ?::uuid", snapshotId)
        jdbc.update(
            "insert into jobs (id, workspace_id, type, status, payload, finished_at) values (?, ?, 'application.match', 'SUCCEEDED', ?::jsonb, now())",
            UUID.randomUUID(),
            workspaceId,
            """{"applicationId":"$soon"}""",
        )
        val after = (dashboard()["attention"] as List<Map<String, Any?>>).map { it["code"] }
        assertThat(after).containsExactly("DEADLINES_SOON", "REVIEWS_PENDING", "ENTRIES_WITHOUT_PROJECTS")
    }

    @Test
    fun `other workspaces do not leak into the summary`() {
        support.create(
            workspaceId,
            "/api/v1/career-entries",
            """{"type":"EMPLOYMENT","title":"Engineer","startDate":"2022-01-01"}""",
        )
        val other = support.newWorkspace()
        val d = support.getJson(other, "/api/v1/dashboard")
        assertThat(d.kpi("careerEntries")["total"]).isEqualTo(0)
    }
}
