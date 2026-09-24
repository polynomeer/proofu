package com.proofu.api.search

import com.proofu.api.identity.WorkspaceContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class SearchHit(
    val id: UUID,
    val title: String,
    val subtitle: String?,
    val href: String,
)

data class SearchGroup(
    val total: Int,
    val items: List<SearchHit>,
)

data class SearchResults(
    val query: String,
    val careerEntries: SearchGroup,
    val projects: SearchGroup,
    val skills: SearchGroup,
    val evidence: SearchGroup,
    val postings: SearchGroup,
)

/**
 * The one search box in the top bar. Each kind of record is queried on its own so a group can
 * report how many it holds, and every query is scoped to the caller's workspace and skips the
 * trash. Matching is a case-insensitive substring: users search for fragments of a technology
 * or a company, which stemming-free full text would miss ('Kotlin' in 'Kotlin/JVM').
 */
@Service
class SearchService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun search(
        workspace: WorkspaceContext,
        query: String,
        limit: Int,
    ): SearchResults {
        val w = workspace.workspaceId.value
        val like = "%${query.trim().lowercase()}%"
        return SearchResults(
            query = query.trim(),
            careerEntries =
                group(
                    "career_entries",
                    """
                    where workspace_id = ? and deleted_at is null
                      and (lower(title) like ? or lower(coalesce(organization, '')) like ?)
                    """.trimIndent(),
                    listOf(w, like, like),
                    "select id, title, organization as subtitle from career_entries",
                    "order by start_date desc, id desc",
                    limit,
                ) { "/career/$it" },
            projects =
                group(
                    "projects",
                    """
                    where workspace_id = ? and deleted_at is null
                      and (lower(name) like ? or lower(role) like ? or lower(summary) like ?)
                    """.trimIndent(),
                    listOf(w, like, like, like),
                    "select id, name as title, role as subtitle from projects",
                    "order by coalesce(start_date, date '9999-12-31') desc, id desc",
                    limit,
                ) { "/projects/$it" },
            skills =
                group(
                    "skills",
                    """
                    where workspace_id = ? and deleted_at is null
                      and (lower(canonical_name) like ? or exists (
                            select 1 from unnest(aliases) as a where lower(a) like ?))
                    """.trimIndent(),
                    listOf(w, like, like),
                    "select id, canonical_name as title, category as subtitle from skills",
                    "order by created_at desc, id desc",
                    limit,
                ) { "/career/skills" },
            evidence =
                group(
                    "evidence",
                    """
                    where workspace_id = ? and deleted_at is null
                      and (lower(title) like ? or lower(coalesce(body, '')) like ?)
                    """.trimIndent(),
                    listOf(w, like, like),
                    "select id, title, type as subtitle from evidence",
                    "order by captured_at desc, id desc",
                    limit,
                ) { "/evidence/$it" },
            postings =
                group(
                    "job_postings",
                    """
                    where workspace_id = ? and deleted_at is null
                      and (lower(company) like ? or lower(role_title) like ?)
                    """.trimIndent(),
                    listOf(w, like, like),
                    "select id, role_title as title, company as subtitle from job_postings",
                    "order by updated_at desc, id desc",
                    limit,
                ) { "/jobs/$it" },
        )
    }

    private fun group(
        table: String,
        where: String,
        args: List<Any>,
        select: String,
        order: String,
        limit: Int,
        href: (UUID) -> String,
    ): SearchGroup {
        val total =
            jdbc.queryForObject("select count(*) from $table $where", Int::class.java, *args.toTypedArray()) ?: 0
        if (total == 0) return SearchGroup(0, emptyList())
        val items =
            jdbc.query(
                "$select $where $order limit ?",
                { rs, _ ->
                    val id = rs.getObject("id", UUID::class.java)
                    SearchHit(id, rs.getString("title"), rs.getString("subtitle"), href(id))
                },
                *(args + limit).toTypedArray(),
            )
        return SearchGroup(total, items)
    }
}
