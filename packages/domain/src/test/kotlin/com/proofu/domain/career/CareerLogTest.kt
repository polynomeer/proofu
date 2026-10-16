package com.proofu.domain.career

import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.CareerLogId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class CareerLogTest {
    private val ws = Fixtures.workspaceA
    private val id = CareerLogId(Fixtures.uuid(10))
    private val entryId = CareerEntryId(Fixtures.uuid(11))
    private val projectId = ProjectId(Fixtures.uuid(12))
    private val achievementId = AchievementId(Fixtures.uuid(13))
    private val evidenceId = EvidenceId(Fixtures.uuid(14))
    private val today = LocalDate.of(2026, 10, 15)
    private val now = Instant.parse("2026-10-15T09:00:00Z")

    private fun log(
        body: String = "결제 실패 알림을 큐로 옮겨 재시도를 붙였다",
        occurredAt: LocalDate = today,
        links: List<CareerLogLink> = emptyList(),
    ) = CareerLog.written(id, ws, occurredAt, body, today, links = links)

    @Test
    fun `a log needs something written in it`() {
        assertThatThrownBy { log(body = " ") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { log(body = "") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThat(log().body).isNotBlank()
    }

    @Test
    fun `nothing is written ahead of time`() {
        assertThatThrownBy { log(occurredAt = today.plusDays(1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
            .hasMessageContaining("future")
        assertThat(log(occurredAt = today).occurredAt).isEqualTo(today)
        assertThat(log(occurredAt = today.minusYears(2)).occurredAt).isEqualTo(today.minusYears(2))
    }

    @Test
    fun `links name the records it belongs to and never the same one twice`() {
        val links = CareerLog.linksTo(listOf(entryId), listOf(projectId))
        assertThat(log(links = links).links).containsExactly(
            CareerLogLink(CareerLogLinkType.CAREER_ENTRY, entryId.value),
            CareerLogLink(CareerLogLinkType.PROJECT, projectId.value),
        )
        assertThatThrownBy { log(links = CareerLog.linksTo(listOf(entryId, entryId))) }
            .isInstanceOf(DomainRuleViolation::class.java)
        // The same uuid under two kinds is two different records, so it is not a repeat.
        val sameId = CareerLog.linksTo(listOf(entryId), listOf(ProjectId(entryId.value)))
        assertThat(log(links = sameId).links).hasSize(2)
    }

    @Test
    fun `a work journal is confidential by default and needs consent to reach a model`() {
        val log = log()
        assertThat(log.sensitivity).isEqualTo(Sensitivity.CONFIDENTIAL)
        assertThat(log.mayEnterAiContext(AiConsent.NONE)).isFalse()
        assertThat(log.mayEnterAiContext(AiConsent.CONFIDENTIAL)).isTrue()
        assertThat(log.copy(sensitivity = Sensitivity.INTERNAL).mayEnterAiContext(AiConsent.NONE)).isTrue()
        assertThat(log.copy(sensitivity = Sensitivity.RESTRICTED).mayEnterAiContext(AiConsent.CONFIDENTIAL))
            .isFalse()
    }

    @Test
    fun `promotion is recorded once, whichever record it produced`() {
        val promoted = log().promotedToAchievement(achievementId, now)
        assertThat(promoted.isPromoted).isTrue()
        assertThat(promoted.promotion?.promotedAt).isEqualTo(now)
        assertThat(promoted.promotion?.target)
            .isEqualTo(CareerLogPromotion.Target(CareerLogPromotionType.ACHIEVEMENT, achievementId.value))

        val asEvidence = log().promotedToEvidence(evidenceId, now)
        assertThat(asEvidence.promotion?.target?.type).isEqualTo(CareerLogPromotionType.EVIDENCE)

        assertThatThrownBy { promoted.promotedToAchievement(achievementId, now) }
            .isInstanceOf(DomainRuleViolation::class.java)
            .hasMessageContaining("already promoted")
    }

    @Test
    fun `purging the promoted record clears the pointer and keeps the date`() {
        val purged = log().promotedToAchievement(achievementId, now).promotionTargetPurged()
        assertThat(purged.promotion?.target).isNull()
        assertThat(purged.promotion?.promotedAt).isEqualTo(now)
        assertThat(purged.isPromoted).isTrue()
        assertThatThrownBy { log().promotionTargetPurged() }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `editing bumps the revision, keeps identity and is not a way to promote`() {
        val log = log()
        val edited = log.revisedTo(log.copy(body = "재시도 횟수를 3회로 줄였다"))
        assertThat(edited.revision).isEqualTo(Revision.INITIAL.next())
        assertThat(edited.body).isEqualTo("재시도 횟수를 3회로 줄였다")

        assertThatThrownBy { log.revisedTo(log.copy(id = CareerLogId(Fixtures.uuid(99)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { log.revisedTo(log.copy(workspaceId = WorkspaceId(Fixtures.uuid(98)))) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { log.revisedTo(log.promotedToAchievement(achievementId, now)) }
            .isInstanceOf(DomainRuleViolation::class.java)
            .hasMessageContaining("not an edit")
    }
}
