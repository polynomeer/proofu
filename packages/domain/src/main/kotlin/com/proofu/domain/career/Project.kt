package com.proofu.domain.career

import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.ProjectId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.LocalDate

/** External reference attached to a project (repository, demo, article). Not evidence by itself. */
data class ProjectLink(
    val label: String,
    val url: String,
) {
    init {
        domainRequire(label.isNotBlank()) { "project link label must not be blank" }
        domainRequire(url.startsWith("https://") || url.startsWith("http://")) {
            "project link url must be an absolute http(s) url"
        }
    }
}

/** A unit of work with a goal, a role and outcomes. Optionally attached to one Career Entry. */
data class Project(
    val id: ProjectId,
    val workspaceId: WorkspaceId,
    val name: String,
    val role: String,
    val summary: String,
    val careerEntryId: CareerEntryId? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val teamSize: Int? = null,
    val links: List<ProjectLink> = emptyList(),
    val visibility: Visibility = Visibility.PRIVATE,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(name.isNotBlank()) { "project name must not be blank" }
        domainRequire(role.isNotBlank()) { "project role must not be blank" }
        domainRequire(summary.isNotBlank()) { "project summary must not be blank" }
        domainRequire(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
            "project end date $endDate must not be before start date $startDate"
        }
        domainRequire(teamSize == null || teamSize > 0) { "project team size must be positive" }
        domainRequire(links.size <= MAX_LINKS) { "a project may carry at most $MAX_LINKS links" }
    }

    /** A project attached to a career entry must belong to the same workspace as that entry. */
    fun attachedTo(entry: CareerEntry): Boolean = careerEntryId == entry.id && workspaceId == entry.workspaceId

    companion object {
        const val MAX_LINKS = 20
    }
}
