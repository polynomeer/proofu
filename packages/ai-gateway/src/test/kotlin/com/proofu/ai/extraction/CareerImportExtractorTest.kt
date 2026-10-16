package com.proofu.ai.extraction

import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewaySettings
import com.proofu.ai.AiUsageSnapshot
import com.proofu.ai.model.FakeModelClient
import com.proofu.ai.model.ModelOutcome
import com.proofu.ai.model.ModelUsage
import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.common.Uuid7IdGenerator
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class CareerImportExtractorTest {
    private val fake = FakeModelClient()
    private val ids = Uuid7IdGenerator()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "fake"),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = { },
            clock = Clock.systemUTC(),
            ids = ids,
            client = fake,
        )
    private val extractor = CareerImportExtractor(gateway)
    private val workspace = WorkspaceId(UUID.randomUUID())
    private val jobId = UUID.randomUUID()
    private val sourceId get() = "resume:$jobId"
    private val resume =
        """
        홍길동 gildong@example.com 010-1234-5678

        경력
        2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어
        결제 시스템을 재작성했습니다.

        학력
        2013.03 - 2017.02 프루프대학교 컴퓨터공학 학사
        """.trimIndent()

    private fun reply(vararg items: String) =
        ModelOutcome.Completed(
            """{"items":[${items.joinToString(",")}]}""",
            "claude-opus-5",
            ModelUsage(100, 20, 0, 0),
            truncated = false,
        )

    private fun item(
        title: String = "백엔드 엔지니어",
        type: String = "EMPLOYMENT",
        organization: String? = "프루프 주식회사",
        start: String = "2020-03",
        end: String? = "2023-02",
        quote: String = "2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어",
        source: String = sourceId,
    ) = buildString {
        append("""{"type":"$type","title":"$title","startDate":"$start","quote":"$quote",""")
        append(""""confidence":0.9,"sourceId":"$source"""")
        if (organization != null) append(""","organization":"$organization"""")
        if (end != null) append(""","endDate":"$end"""")
        append("}")
    }

    @Test
    fun `a proposal keeps the resume's own words and a date the form can use`() {
        fake.enqueue(reply(item()))

        val result = extractor.propose(workspace, resume, jobId)

        assertThat(result.proposals).hasSize(1)
        val proposal = result.proposals.single()
        assertThat(proposal.type).isEqualTo(CareerEntryType.EMPLOYMENT)
        assertThat(proposal.title).isEqualTo("백엔드 엔지니어")
        assertThat(proposal.organization).isEqualTo("프루프 주식회사")
        // "2020-03" is a month; the form needs a day and March 1st is the honest guess.
        assertThat(proposal.startDate).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(proposal.endDate).isEqualTo(LocalDate.of(2023, 2, 1))
        assertThat(proposal.quote).isEqualTo("2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어")
        assertThat(proposal.warning).isNull()
    }

    @Test
    fun `the contact details are gone before the model is called, and counted`() {
        fake.enqueue(reply(item()))

        val result = extractor.propose(workspace, resume, jobId)

        val sent =
            fake.requests
                .single()
                .documents
                .single()
                .text
        assertThat(sent).doesNotContain("gildong@example.com", "010-1234-5678")
        assertThat(sent).contains("2020.03 - 2023.02", "프루프 주식회사")
        assertThat(result.redacted).isEqualTo(2)
    }

    @Test
    fun `a quote that is not in the resume is kept but flagged`() {
        fake.enqueue(reply(item(quote = "2019.01 - 2020.01 없는회사 / 팀장")))

        val proposal = extractor.propose(workspace, resume, jobId).proposals.single()

        assertThat(proposal.warning).contains("인용문을 원문에서 찾지 못했습니다")
    }

    @Test
    fun `items the server cannot place are dropped with a reason`() {
        fake.enqueue(
            reply(
                item(title = "", quote = "빈 제목"),
                item(title = "알 수 없는 기간", start = "언젠가"),
                item(source = "resume:somewhere-else"),
                item(),
                item(),
            ),
        )

        val result = extractor.propose(workspace, resume, jobId)

        assertThat(result.proposals).hasSize(1)
        assertThat(result.dropped)
            .anyMatch { it.contains("제목이 없는") }
            .anyMatch { it.contains("시작일을 읽을 수 없습니다") }
            .anyMatch { it.contains("출처가 다른") }
            .anyMatch { it.contains("두 번") }
    }

    @Test
    fun `an end date before the start is cleared instead of losing the record`() {
        fake.enqueue(reply(item(start = "2020-03", end = "2019-01")))

        val proposal = extractor.propose(workspace, resume, jobId).proposals.single()

        assertThat(proposal.endDate).isNull()
        assertThat(proposal.warning).contains("종료일이 시작일보다 앞서")
    }

    @Test
    fun `resumes write dates every way there is`() {
        assertThat(CareerImportExtractor.parseDate("2020-03-15")).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(CareerImportExtractor.parseDate("2020.3")).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(CareerImportExtractor.parseDate("2020/03")).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(CareerImportExtractor.parseDate("2020")).isEqualTo(LocalDate.of(2020, 1, 1))
        assertThat(CareerImportExtractor.parseDate("현재")).isNull()
        assertThat(CareerImportExtractor.parseDate("2020-13")).isNull()
        assertThat(CareerImportExtractor.parseDate(null)).isNull()
    }
}

/** The fake provider is what local development and the browser tests run on. */
class CareerImportHeuristicTest {
    private val ids = Uuid7IdGenerator()
    private val gateway =
        AiGatewayFactory.create(
            settings = AiGatewaySettings(provider = "fake"),
            usage = { _, _ -> AiUsageSnapshot(0, 0, 0) },
            recorder = { },
            clock = Clock.systemUTC(),
            ids = ids,
        )
    private val extractor = CareerImportExtractor(gateway)

    @Test
    fun `the dated lines of a resume come back as proposals`() {
        val resume =
            """
            홍길동 gildong@example.com

            경력
            2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어
            결제 시스템을 재작성했습니다.

            2017.03-2020.02 다른회사 / 주니어 개발자

            학력
            2013.03 - 2017.02 프루프대학교 / 컴퓨터공학 학사
            """.trimIndent()

        val result = extractor.propose(WorkspaceId(UUID.randomUUID()), resume, UUID.randomUUID())

        assertThat(result.proposals).hasSize(3)
        val first = result.proposals.first()
        assertThat(first.title).isEqualTo("백엔드 엔지니어")
        assertThat(first.organization).isEqualTo("프루프 주식회사")
        assertThat(first.startDate).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(first.endDate).isEqualTo(LocalDate.of(2023, 2, 1))
        assertThat(first.description).isEqualTo("결제 시스템을 재작성했습니다.")
        assertThat(first.warning).isNull()
        assertThat(result.proposals.last().type).isEqualTo(CareerEntryType.EDUCATION)
        assertThat(result.redacted).isEqualTo(1)
    }
}
