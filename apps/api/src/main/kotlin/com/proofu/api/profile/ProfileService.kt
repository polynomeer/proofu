package com.proofu.api.profile

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.UserId
import com.proofu.domain.identity.Profile
import com.proofu.domain.identity.ProfileLink
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

/**
 * One profile per user, keyed by the caller. Plain SQL: the row is small and its only readers
 * besides this service are the export worker and the ATS check. Values never reach a log.
 */
@Service
class ProfileService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun get(workspace: WorkspaceContext): ProfileResponse =
        find(workspace.userId.value) ?: throw ResourceNotFoundException("profile", workspace.userId.value)

    /** Reads for other services; null when the user has not saved one. */
    @Transactional(readOnly = true)
    fun findDomain(userId: UUID): Profile? = find(userId)?.let { toDomain(it) }

    @Transactional
    fun save(
        workspace: WorkspaceContext,
        request: ProfileRequest,
    ): ProfileResponse {
        val userId = workspace.userId.value
        val current =
            jdbc
                .query(
                    "select version from user_profiles where user_id = ? for update",
                    { rs, _ -> rs.getLong("version") },
                    userId,
                ).firstOrNull()
        if (current != null && request.version != null && request.version != current) {
            throw StaleVersionException("profile", request.version, current)
        }
        val next = (current ?: 0L) + 1
        val profile = request.toDomain(UserId(userId), next)
        val links = mapper.writeValueAsString(profile.links.map { mapOf("label" to it.label, "url" to it.url) })
        jdbc.update(
            """
            insert into user_profiles (user_id, full_name, headline, email, phone, location, links, version)
            values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            on conflict (user_id) do update set
              full_name = excluded.full_name, headline = excluded.headline, email = excluded.email, phone = excluded.phone,
              location = excluded.location, links = excluded.links, version = excluded.version
            """.trimIndent(),
            userId,
            profile.fullName,
            profile.headline,
            profile.email,
            profile.phone,
            profile.location,
            links,
            next,
        )
        // The audit row keeps hashes only; contact details themselves are never written to it.
        audit.record(
            workspace,
            if (current ==
                null
            ) {
                "profile.created"
            } else {
                "profile.updated"
            },
            "profile",
            userId,
            after = mapOf("version" to next),
        )
        return checkNotNull(find(userId))
    }

    private fun find(userId: UUID): ProfileResponse? =
        jdbc.query("select * from user_profiles where user_id = ?", { rs, _ -> row(rs) }, userId).firstOrNull()

    private fun row(rs: ResultSet): ProfileResponse {
        val links = mutableListOf<ProfileLinkDto>()
        for (node in mapper.readTree(rs.getString("links"))) {
            links += ProfileLinkDto(node.get("label").asString(), node.get("url").asString())
        }
        val profile =
            Profile(
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                fullName = rs.getString("full_name"),
                headline = rs.getString("headline"),
                email = rs.getString("email"),
                phone = rs.getString("phone"),
                location = rs.getString("location"),
                links = links.map { ProfileLink(requireNotNull(it.label), requireNotNull(it.url)) },
                version = rs.getLong("version"),
            )
        return ProfileResponse.from(
            profile,
            rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
        )
    }

    private fun toDomain(r: ProfileResponse) =
        Profile(
            userId = UserId(r.userId),
            fullName = r.fullName,
            headline = r.headline,
            email = r.email,
            phone = r.phone,
            location = r.location,
            links = r.links.map { ProfileLink(requireNotNull(it.label), requireNotNull(it.url)) },
            version = r.version,
        )
}
