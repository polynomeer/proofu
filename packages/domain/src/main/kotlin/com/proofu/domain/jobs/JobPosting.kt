package com.proofu.domain.jobs

import com.proofu.domain.common.JobPostingId
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.Instant

/**
 * A posting as the user tracks it: mutable identity (company, role, canonical url) over
 * one or more immutable [JobPostingSnapshot]s. A changed content hash yields a new snapshot.
 */
data class JobPosting(
    val id: JobPostingId,
    val workspaceId: WorkspaceId,
    val company: String,
    val roleTitle: String,
    val canonicalUrl: String? = null,
    val externalPostingId: String? = null,
    val publishedAt: Instant? = null,
) {
    init {
        domainRequire(company.isNotBlank()) { "job posting company must not be blank" }
        domainRequire(roleTitle.isNotBlank()) { "job posting role title must not be blank" }
        domainRequire(
            canonicalUrl == null || canonicalUrl.startsWith("https://") || canonicalUrl.startsWith("http://"),
        ) {
            "job posting canonical url must be an absolute http(s) url"
        }
    }
}
