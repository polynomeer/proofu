package com.proofu.api.dashboard

import com.proofu.api.identity.WorkspaceContext
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.career.CareerEntryType
import com.proofu.domain.evidence.EvidenceType
import com.proofu.domain.evidence.VerificationStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Read model for D01. Everything is a live aggregate over the workspace; nothing is
 * denormalised, so the numbers are always consistent with the lists they link to.
 */
@Service
class DashboardService(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun summary(
        workspace: WorkspaceContext,
        timelineType: CareerEntryType?,
    ): DashboardSummary {
        val ws = workspace.workspaceId.value
        val since = Timestamp.from(Instant.now(clock).minus(30, ChronoUnit.DAYS))
        val kpis =
            DashboardKpis(
                careerEntries = careerEntries(ws, since),
                verifiedEvidence = verifiedEvidence(ws, since),
                activeApplications = activeApplications(ws),
                evidenceCoverage = evidenceCoverage(ws),
            )
        return DashboardSummary(
            kpis = kpis,
            timeline = timeline(ws, timelineType),
            recentEvidence = recentEvidence(ws),
            attention = attention(ws, kpis),
        )
    }

    private fun careerEntries(
        ws: UUID,
        since: Timestamp,
    ): CareerEntriesKpi =
        jdbc.queryForObject(
            """
            select
              (select count(*) from career_entries where workspace_id = ? and deleted_at is null) as total,
              (select count(*) from career_entries where workspace_id = ? and deleted_at is null and created_at >= ?) as recent,
              (select count(*) from projects where workspace_id = ? and deleted_at is null) as projects,
              (select count(*) from achievements where workspace_id = ? and deleted_at is null) as achievements
            """.trimIndent(),
            {
                rs,
                _,
                ->
                CareerEntriesKpi(
                    rs.getInt("total"),
                    rs.getInt("recent"),
                    rs.getInt("projects"),
                    rs.getInt("achievements"),
                )
            },
            ws,
            ws,
            since,
            ws,
            ws,
        )

    private fun verifiedEvidence(
        ws: UUID,
        since: Timestamp,
    ): VerifiedEvidenceKpi =
        jdbc.queryForObject(
            """
            select
              count(*) filter (where verification in ('USER_VERIFIED', 'EXTERNALLY_VERIFIED')) as verified,
              count(*) as total,
              count(*) filter (where verification = 'EXPIRED') as expired,
              count(*) filter (where created_at >= ?) as recent
            from evidence where workspace_id = ? and deleted_at is null
            """.trimIndent(),
            {
                rs,
                _,
                ->
                VerifiedEvidenceKpi(
                    rs.getInt("verified"),
                    rs.getInt("total"),
                    rs.getInt("expired"),
                    rs.getInt("recent"),
                )
            },
            since,
            ws,
        )

    private fun activeApplications(ws: UUID): ActiveApplicationsKpi {
        val terminal = ApplicationStatus.entries.filter { it.isTerminal }.joinToString(",") { "'${it.name}'" }
        return jdbc.queryForObject(
            """
            select
              count(*) filter (where status not in ($terminal)) as total,
              count(*) filter (where status = 'SUBMITTED') as submitted,
              count(*) filter (where status in ('DOCUMENT_PASSED', 'HANDOFF_READY')) as passed
            from applications where workspace_id = ? and deleted_at is null
            """.trimIndent(),
            { rs, _ -> ActiveApplicationsKpi(rs.getInt("total"), rs.getInt("submitted"), rs.getInt("passed")) },
            ws,
        )
    }

    private fun evidenceCoverage(ws: UUID): EvidenceCoverageKpi =
        jdbc.queryForObject(
            """
            select count(*) as claims,
                   count(*) filter (where exists (
                       select 1 from claim_evidence ce join evidence e on e.id = ce.evidence_id
                       where ce.claim_id = c.id and e.deleted_at is null)) as supported
            from claims c where c.workspace_id = ? and c.deleted_at is null
            """.trimIndent(),
            { rs, _ ->
                val claims = rs.getInt("claims")
                val supported = rs.getInt("supported")
                EvidenceCoverageKpi(claims, supported, if (claims == 0) 0 else supported * 100 / claims)
            },
            ws,
        )

    private fun timeline(
        ws: UUID,
        type: CareerEntryType?,
    ): List<TimelineItem> =
        jdbc.query(
            """
            with entry_claims as (
              select ce.id as entry_id, c.id as claim_id
              from career_entries ce
              join claim_sources cs on
                   (cs.source_type = 'CAREER_ENTRY' and cs.source_id = ce.id)
                or (cs.source_type = 'PROJECT' and cs.source_id in
                      (select id from projects where career_entry_id = ce.id and deleted_at is null))
                or (cs.source_type = 'ACHIEVEMENT' and cs.source_id in
                      (select a.id from achievements a join projects p on p.id = a.project_id
                       where p.career_entry_id = ce.id and a.deleted_at is null and p.deleted_at is null))
              join claims c on c.id = cs.claim_id and c.deleted_at is null
              where ce.workspace_id = ?
            )
            select ce.id, ce.type, ce.title, ce.organization, ce.start_date, ce.end_date,
                   (select count(*) from projects p where p.career_entry_id = ce.id and p.deleted_at is null) as project_count,
                   (select count(distinct claim_id) from entry_claims ec where ec.entry_id = ce.id) as claim_count,
                   (select count(distinct x.evidence_id) from entry_claims ec
                      join claim_evidence x on x.claim_id = ec.claim_id
                      join evidence e on e.id = x.evidence_id and e.deleted_at is null
                    where ec.entry_id = ce.id) as evidence_count
            from career_entries ce
            where ce.workspace_id = ? and ce.deleted_at is null
              and (cast(? as varchar) is null or ce.type = cast(? as varchar))
            order by ce.start_date desc, ce.id desc
            limit $TIMELINE_LIMIT
            """.trimIndent(),
            { rs, _ ->
                TimelineItem(
                    id = rs.getObject("id", UUID::class.java),
                    type = CareerEntryType.valueOf(rs.getString("type")),
                    title = rs.getString("title"),
                    organization = rs.getString("organization"),
                    startDate = rs.getDate("start_date").toLocalDate(),
                    endDate = rs.getDate("end_date")?.toLocalDate(),
                    projectCount = rs.getInt("project_count"),
                    claimCount = rs.getInt("claim_count"),
                    evidenceCount = rs.getInt("evidence_count"),
                )
            },
            ws,
            ws,
            type?.name,
            type?.name,
        )

    private fun recentEvidence(ws: UUID): List<RecentEvidenceItem> =
        jdbc.query(
            """
            select id, title, type, verification, captured_at from evidence
            where workspace_id = ? and deleted_at is null
            order by created_at desc, id desc limit $RECENT_EVIDENCE_LIMIT
            """.trimIndent(),
            { rs, _ ->
                RecentEvidenceItem(
                    id = rs.getObject("id", UUID::class.java),
                    title = rs.getString("title"),
                    type = EvidenceType.valueOf(rs.getString("type")),
                    verification = VerificationStatus.valueOf(rs.getString("verification")),
                    capturedAt = rs.getTimestamp("captured_at").toInstant(),
                )
            },
            ws,
        )

    /** At most three, ordered by how directly they hurt the "evidence first" promise. */
    private fun attention(
        ws: UUID,
        kpis: DashboardKpis,
    ): List<AttentionItem> {
        if (kpis.careerEntries.total == 0) return listOf(AttentionItem(AttentionCode.NO_CAREER_ENTRIES, 0))
        val entriesWithoutProjects =
            jdbc.queryForObject(
                """
                select count(*) from career_entries ce
                where ce.workspace_id = ? and ce.deleted_at is null and ce.type = 'EMPLOYMENT'
                  and not exists (select 1 from projects p where p.career_entry_id = ce.id and p.deleted_at is null)
                """.trimIndent(),
                Int::class.java,
                ws,
            ) ?: 0
        return listOfNotNull(
            (kpis.evidenceCoverage.claims - kpis.evidenceCoverage.supported)
                .takeIf { it > 0 }
                ?.let { AttentionItem(AttentionCode.UNSUPPORTED_CLAIMS, it) },
            jdbc
                .queryForObject(
                    "select count(*) from evidence where workspace_id = ? and deleted_at is null and verification = 'UNVERIFIED'",
                    Int::class.java,
                    ws,
                ).takeIf { it != null && it > 0 }
                ?.let { AttentionItem(AttentionCode.UNVERIFIED_EVIDENCE, it) },
            entriesWithoutProjects.takeIf { it > 0 }?.let { AttentionItem(AttentionCode.ENTRIES_WITHOUT_PROJECTS, it) },
        ).take(MAX_ATTENTION)
    }

    private companion object {
        const val TIMELINE_LIMIT = 5
        const val RECENT_EVIDENCE_LIMIT = 4
        const val MAX_ATTENTION = 3
    }
}
