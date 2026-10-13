package com.proofu.renderer

import com.proofu.domain.common.UserId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.ExportFormat
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.identity.Profile
import com.proofu.domain.identity.ProfileLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.util.UUID

/**
 * Writes representative Korean and English exports (PDF and DOCX) for the visual check in the
 * beta checklist; only when EXPORT_SAMPLE_DIR is set. Each file also passes ExportValidator, so a
 * sample that reads wrong is a rendering problem, not a text one. Synthetic data only.
 */
class ExportSampleWriter {
    private val dir get() = File(System.getenv("EXPORT_SAMPLE_DIR")).apply { mkdirs() }

    private fun block(
        id: String,
        text: String,
    ) = GeneratedBlock(id, text, certainty = Certainty.UNSUPPORTED, approvedByUser = true)

    private val koProfile =
        Profile(
            UserId(UUID.randomUUID()),
            "김지원",
            headline = "결제·정산 백엔드 엔지니어",
            email = "jiwon@example.com",
            phone = "010-0000-0000",
            links = listOf(ProfileLink("GitHub", "https://github.com/example")),
        )

    private val enProfile =
        Profile(
            UserId(UUID.randomUUID()),
            "Jiwon Kim",
            headline = "Backend engineer, payments and settlement",
            email = "jiwon@example.com",
            phone = "+82 10-0000-0000",
            links = listOf(ProfileLink("GitHub", "https://github.com/example")),
        )

    private val koResume =
        GeneratedOutput(
            listOf(
                block("summary-1", "결제·정산 도메인에서 7년간 Kotlin과 Spring으로 서버를 만들어 왔습니다. 배치 중심 정산을 이벤트 처리로 옮긴 경험이 있습니다."),
                block("skills-1", "대용량 정산 파이프라인 설계 — 일 300만 건, 지연 99p 1.2초 이하로 운영"),
                block("skills-2", "RDBMS 모델링과 쿼리 튜닝 (PostgreSQL, 인덱스·파티셔닝)"),
                block(
                    "career-1",
                    "한결솔루션 · 시니어 백엔드 엔지니어 (2023.03 – 현재)\n정산 플랫폼 팀을 이끌며 일 마감 정산 배치를 Kafka 기반 이벤트 처리로 전환해, " +
                        "정산 확정 시점을 익일 오전에서 당일 자정 이전으로 앞당겼습니다.",
                ),
                block("career-2", "새벽마켓 · 백엔드 엔지니어 (2019.07 – 2023.02)\n주문·재고 API를 운영하며 검색 인덱싱을 변경분 색인 방식으로 바꿨습니다."),
                block(
                    "projects-1",
                    // Long enough to wrap many times and push the document onto a second page.
                    List(8) {
                        "정산 이벤트 파이프라인 전환 — 문제: 마감 배치가 길어져 정산 확정이 늦어졌습니다. 행동: 이벤트 단위 처리와 대사 자동화를 설계했습니다. " +
                            "결과: 확정 시점을 반나절 앞당기고 수작업 대사를 일 1회 확인으로 줄였습니다."
                    }.joinToString(" "),
                ),
                block("techs-1", "Kotlin, Java 21, Spring Boot, PostgreSQL, Kafka, Redis, AWS (ECS·RDS)"),
                block("education-1", "한국대학교 컴퓨터공학과 학사 (2012 – 2019)"),
            ),
        )

    private val enResume =
        GeneratedOutput(
            listOf(
                block(
                    "summary-1",
                    "Backend engineer with seven years of Kotlin and Spring in payments and settlement, including moving batch settlement to event processing.",
                ),
                block("skills-1", "High-volume settlement pipelines — 3M records/day, p99 latency under 1.2s"),
                block(
                    "career-1",
                    "Hangyeol Solutions · Senior Backend Engineer (Mar 2023 – present)\nLed the move from end-of-day batches to Kafka events; settlement now closes before midnight.",
                ),
                block(
                    "projects-1",
                    List(6) {
                        "Settlement event pipeline — Problem: the closing batch kept growing. " +
                            "Action: designed per-event processing and automated reconciliation. " +
                            "Result: settlement closes half a day earlier; manual reconciliation dropped to one daily check."
                    }.joinToString(" "),
                ),
                block("techs-1", "Kotlin, Java 21, Spring Boot, PostgreSQL, Kafka, Redis, AWS (ECS, RDS)"),
                block("education-1", "B.S. in Computer Science, Hanguk University (2012 – 2019)"),
            ),
        )

    private val koCover =
        GeneratedOutput(
            listOf(
                block(
                    "motivation-1",
                    "결제·정산을 데이터로 단단하게 만드는 일에 지원합니다. English words, 숫자 12,345와 기호 (·, –, %)가 섞여도 그대로 보여야 합니다.",
                ),
                block("experience-1", "정산 배치를 이벤트 처리로 전환해 확정 시점을 앞당겼습니다.\n지표 정의와 장애 대응 절차를 직접 맡았습니다."),
                block("contribution-1", "기록된 사실 범위에서 정산 안정성과 운영 자동화에 기여하겠습니다."),
            ),
        )

    private fun write(
        name: String,
        type: DocumentType,
        language: String,
        title: String,
        output: GeneratedOutput,
        profile: Profile,
    ) {
        val doc =
            RenderableDocument
                .of(
                    title,
                    type,
                    language,
                    "예시 주식회사",
                    "백엔드 엔지니어",
                    DocumentTemplate.latest(type, language),
                    output,
                ).copy(contact = RenderableContact.of(profile))
        listOf(ExportFormat.PDF to "pdf", ExportFormat.DOCX to "docx").forEach { (format, ext) ->
            val rendered = Renderers.forFormat(format)!!.render(doc)
            // Written before the check so a failing sample can still be looked at.
            File(dir, "$name.$ext").writeBytes(rendered.bytes)
            File(dir, "$name.$ext.txt").writeText(ExportValidator.extractText(rendered))
            problems += ExportValidator.validate(rendered, doc).map { "$name.$ext: $it" }
        }
    }

    private val problems = mutableListOf<String>()

    @Test
    @EnabledIfEnvironmentVariable(named = "EXPORT_SAMPLE_DIR", matches = ".+")
    fun `write the samples`() {
        write("resume-ko", DocumentType.RESUME, "ko", "이력서", koResume, koProfile)
        write("resume-en", DocumentType.RESUME, "en", "Resume", enResume, enProfile)
        write("cover-letter-ko", DocumentType.COVER_LETTER, "ko", "자기소개서", koCover, koProfile)
        assertThat(problems).isEmpty()
    }
}
