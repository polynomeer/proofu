package com.proofu.api.dashboard

import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CareerEntriesKpi(
    val total: Int,
    val addedLast30Days: Int,
    val projects: Int,
    val achievements: Int,
)

data class VerifiedEvidenceKpi(
    val verified: Int,
    val total: Int,
    val expired: Int,
    val addedLast30Days: Int,
)

data class ActiveApplicationsKpi(
    val total: Int,
    val submitted: Int,
    val documentPassed: Int,
)

data class EvidenceCoverageKpi(
    val claims: Int,
    val supported: Int,
    /** supported / claims × 100, 0 when there are no claims. Never a probability of anything. */
    val ratio: Int,
)

data class DashboardKpis(
    val careerEntries: CareerEntriesKpi,
    val verifiedEvidence: VerifiedEvidenceKpi,
    val activeApplications: ActiveApplicationsKpi,
    val evidenceCoverage: EvidenceCoverageKpi,
)

data class TimelineItem(
    val id: UUID,
    val type: CareerEntryType,
    val title: String,
    val organization: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val projectCount: Int,
    val claimCount: Int,
    val evidenceCount: Int,
)

data class RecentEvidenceItem(
    val id: UUID,
    val title: String,
    val type: EvidenceType,
    val verification: VerificationStatus,
    val capturedAt: Instant,
)

/** Ordered by how urgently the user should act; the dashboard shows the first few. */
enum class AttentionCode {
    NO_CAREER_ENTRIES,
    DEADLINES_SOON,
    REVIEWS_PENDING,
    DRAFT_REQUIREMENTS,
    MATCH_NOT_RUN,
    BLOCKS_PENDING_APPROVAL,
    UNSUPPORTED_CLAIMS,
    UNVERIFIED_EVIDENCE,
    ENTRIES_WITHOUT_PROJECTS,
}

data class AttentionItem(
    val code: AttentionCode,
    val count: Int,
)

data class DashboardSummary(
    val kpis: DashboardKpis,
    val timeline: List<TimelineItem>,
    val recentEvidence: List<RecentEvidenceItem>,
    val attention: List<AttentionItem>,
)
