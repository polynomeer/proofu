package com.proofu.worker.export

import com.proofu.domain.common.UserId
import com.proofu.domain.identity.Profile
import com.proofu.domain.identity.ProfileLink
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** The workspace owner's profile, for document headers. Read only; values never reach a log. */
@Component
class ProfileReader(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun forWorkspace(workspaceId: UUID): Profile? =
        jdbc
            .query(
                """
                select p.* from user_profiles p
                join workspaces w on w.owner_user_id = p.user_id
                where w.id = ?
                """.trimIndent(),
                { rs, _ ->
                    val links = mutableListOf<ProfileLink>()
                    for (node in mapper.readTree(rs.getString("links"))) {
                        links += ProfileLink(node.get("label").asString(), node.get("url").asString())
                    }
                    Profile(
                        userId = UserId(rs.getObject("user_id", UUID::class.java)),
                        fullName = rs.getString("full_name"),
                        headline = rs.getString("headline"),
                        email = rs.getString("email"),
                        phone = rs.getString("phone"),
                        location = rs.getString("location"),
                        links = links,
                        version = rs.getLong("version"),
                    )
                },
                workspaceId,
            ).firstOrNull()
}
