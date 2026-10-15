package com.proofu.domain.career

import com.proofu.domain.common.AchievementId
import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.CareerLogId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.EvidenceId
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The kind of career record a log is filed under. */
enum class CareerLogLinkType {
    CAREER_ENTRY,
    PROJECT,
}

/**
 * Where a log happened. Stored as (type, id) like [com.proofu.domain.evidence.ClaimSource] even
 * though the table keeps two nullable foreign keys: the database can enforce the keys, and the
 * domain does not care which column a link came from.
 */
data class CareerLogLink(
    val type: CareerLogLinkType,
    val id: UUID,
)

/** What a log was promoted into. */
enum class CareerLogPromotionType {
    ACHIEVEMENT,
    EVIDENCE,
}

/**
 * That a log became an achievement (or evidence), and when. [target] is null once that record
 * was permanently deleted: promotion was a copy, so the record never needed the log, and the log
 * keeps the date because it happened (docs/data/retention.md §커리어 로그).
 */
data class CareerLogPromotion(
    val promotedAt: Instant,
    val target: Target? = null,
) {
    data class Target(
        val type: CareerLogPromotionType,
        val id: UUID,
    )
}

/**
 * A line the user wrote while the work was happening, so achievements are not reconstructed from
 * memory at application time (F20, docs/domain/career-data-model.md §커리어 로그).
 *
 * A log has no audience and therefore no visibility: it is not published, shared or exported. Its
 * sensitivity defaults to [Sensitivity.CONFIDENTIAL] rather than INTERNAL because a work journal
 * picks up the employer's information unless the user says otherwise, which keeps it out of an AI
 * context without consent.
 *
 * The constructor reads stored rows; [written] is the path for a new log.
 */
data class CareerLog(
    val id: CareerLogId,
    val workspaceId: WorkspaceId,
    val occurredAt: LocalDate,
    val body: String,
    val sensitivity: Sensitivity = Sensitivity.CONFIDENTIAL,
    val links: List<CareerLogLink> = emptyList(),
    val promotion: CareerLogPromotion? = null,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(body.isNotBlank()) { "career log body must not be blank" }
        domainRequire(links.distinctBy { it.type to it.id }.size == links.size) {
            "career log links must not repeat the same record"
        }
    }

    val isPromoted: Boolean get() = promotion != null

    /** Whether this log may be sent to a model under the workspace's consent. */
    fun mayEnterAiContext(consent: AiConsent): Boolean = sensitivity.allowedInAiContext(consent)

    /**
     * The log produced an achievement (or evidence). The target is a copy that stands on its own,
     * so this only records that it happened — and happens once, because a second promotion would
     * be a second copy of the same line.
     */
    fun promotedTo(
        target: CareerLogPromotion.Target,
        at: Instant,
    ): CareerLog {
        domainRequire(promotion == null) { "career log ${id.value} was already promoted" }
        return copy(promotion = CareerLogPromotion(at, target))
    }

    fun promotedToAchievement(
        achievementId: AchievementId,
        at: Instant,
    ): CareerLog = promotedTo(CareerLogPromotion.Target(CareerLogPromotionType.ACHIEVEMENT, achievementId.value), at)

    fun promotedToEvidence(
        evidenceId: EvidenceId,
        at: Instant,
    ): CareerLog = promotedTo(CareerLogPromotion.Target(CareerLogPromotionType.EVIDENCE, evidenceId.value), at)

    /** The promoted record was permanently deleted. The pointer goes, the date stays. */
    fun promotionTargetPurged(): CareerLog {
        val promoted = promotion ?: throw DomainRuleViolation("career log ${id.value} was never promoted")
        return copy(promotion = promoted.copy(target = null))
    }

    /**
     * The user edited the log. Identity and workspace stay, and promotion is not an edit: it is
     * recorded by [promotedTo] when the copy is made.
     */
    fun revisedTo(next: CareerLog): CareerLog {
        domainRequire(next.id == id && next.workspaceId == workspaceId) { "career log identity must not change" }
        domainRequire(next.promotion == promotion) { "promotion is not an edit; use promotedTo" }
        return next.copy(revision = revision.next())
    }

    companion object {
        /**
         * A new log. Nothing is written ahead of time, so [occurredAt] may not be after [today];
         * the caller passes the day rather than the domain reading a clock.
         */
        fun written(
            id: CareerLogId,
            workspaceId: WorkspaceId,
            occurredAt: LocalDate,
            body: String,
            today: LocalDate,
            sensitivity: Sensitivity = Sensitivity.CONFIDENTIAL,
            links: List<CareerLogLink> = emptyList(),
        ): CareerLog {
            domainRequire(!occurredAt.isAfter(today)) {
                "a career log cannot be written for the future: $occurredAt is after $today"
            }
            return CareerLog(id, workspaceId, occurredAt, body, sensitivity, links)
        }

        /** The link set, built from typed ids; the set is replaced whole, like every career link. */
        fun linksTo(
            careerEntryIds: Collection<CareerEntryId> = emptyList(),
            projectIds: Collection<ProjectId> = emptyList(),
        ): List<CareerLogLink> =
            careerEntryIds.map { CareerLogLink(CareerLogLinkType.CAREER_ENTRY, it.value) } +
                projectIds.map { CareerLogLink(CareerLogLinkType.PROJECT, it.value) }
    }
}
