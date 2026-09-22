package com.proofu.worker.jobs

import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.identity.WorkspaceSettings
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID

/** The workspace's S01 preferences as AI jobs need them; defaults when nothing was saved. */
@Component
class WorkspaceSettingsReader(
    private val jdbc: JdbcTemplate,
) {
    fun forWorkspace(workspaceId: UUID): WorkspaceSettings =
        jdbc
            .query(
                "select ai_consent, ai_consent_at, default_visibility, version from workspace_settings where workspace_id = ?",
                { rs, _ ->
                    WorkspaceSettings(
                        workspaceId = WorkspaceId(workspaceId),
                        aiConsent = AiConsent.valueOf(rs.getString("ai_consent")),
                        aiConsentAt = rs.getObject("ai_consent_at", OffsetDateTime::class.java)?.toInstant(),
                        defaultVisibility = Visibility.valueOf(rs.getString("default_visibility")),
                        version = rs.getLong("version"),
                    )
                },
                workspaceId,
            ).firstOrNull() ?: WorkspaceSettings(WorkspaceId(workspaceId))

    fun consentFor(workspaceId: UUID): AiConsent = forWorkspace(workspaceId).aiConsent
}
