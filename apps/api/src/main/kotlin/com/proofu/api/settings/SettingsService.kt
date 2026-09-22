package com.proofu.api.settings

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.AuthProperties
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.identity.RetentionPolicy
import com.proofu.domain.identity.WorkspaceSettings
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class RetentionRequest(
    @field:NotNull @field:Min(7) @field:Max(365) val trashDays: Int?,
    @field:NotNull @field:Min(1) @field:Max(90) val exportDays: Int?,
)

data class SettingsRequest(
    @field:NotNull val aiConsent: AiConsent?,
    @field:NotNull val defaultVisibility: Visibility?,
    @field:NotNull @field:Valid val retention: RetentionRequest?,
    val version: Long? = null,
)

data class RetentionResponse(
    val trashDays: Int,
    val exportDays: Int,
)

data class SettingsResponse(
    val workspaceId: UUID,
    val aiConsent: AiConsent,
    val aiConsentAt: Instant?,
    val defaultVisibility: Visibility,
    val retention: RetentionResponse,
    val version: Long,
) {
    companion object {
        fun from(s: WorkspaceSettings) =
            SettingsResponse(
                s.workspaceId.value,
                s.aiConsent,
                s.aiConsentAt,
                s.defaultVisibility,
                RetentionResponse(s.retention.trashDays, s.retention.exportDays),
                s.version,
            )
    }
}

/** S01 preferences. Defaults are answered without a row; the first save creates it. */
@Service
class SettingsService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditLog,
    private val auth: AuthProperties,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(workspace: WorkspaceContext): SettingsResponse = SettingsResponse.from(current(workspace.workspaceId.value))

    /** For other services: the effective settings of a workspace. */
    @Transactional(readOnly = true)
    fun current(workspaceId: UUID): WorkspaceSettings =
        jdbc
            .query(
                """
                select ai_consent, ai_consent_at, default_visibility, trash_retention_days, export_retention_days, version
                from workspace_settings where workspace_id = ?
                """.trimIndent(),
                { rs, _ ->
                    WorkspaceSettings(
                        workspaceId = WorkspaceId(workspaceId),
                        aiConsent = AiConsent.valueOf(rs.getString("ai_consent")),
                        aiConsentAt = rs.getObject("ai_consent_at", OffsetDateTime::class.java)?.toInstant(),
                        defaultVisibility = Visibility.valueOf(rs.getString("default_visibility")),
                        retention =
                            RetentionPolicy(rs.getInt("trash_retention_days"), rs.getInt("export_retention_days")),
                        version = rs.getLong("version"),
                    )
                },
                workspaceId,
            ).firstOrNull() ?: WorkspaceSettings(WorkspaceId(workspaceId))

    @Transactional
    fun save(
        workspace: WorkspaceContext,
        request: SettingsRequest,
    ): SettingsResponse {
        val workspaceId = workspace.workspaceId.value
        jdbc.query("select 1 from workspace_settings where workspace_id = ? for update", { _, _ -> 1 }, workspaceId)
        val previous = current(workspaceId)
        if (request.version != null && request.version != previous.version) {
            throw StaleVersionException("settings", request.version, previous.version)
        }
        val consent = requireNotNull(request.aiConsent)
        val next =
            WorkspaceSettings(
                workspaceId = WorkspaceId(workspaceId),
                aiConsent = consent,
                aiConsentAt =
                    when (consent) {
                        AiConsent.NONE -> null
                        else -> previous.aiConsentAt?.takeIf { previous.aiConsent == consent } ?: Instant.now(clock)
                    },
                defaultVisibility = requireNotNull(request.defaultVisibility),
                retention =
                    requireNotNull(request.retention).let {
                        RetentionPolicy(requireNotNull(it.trashDays), requireNotNull(it.exportDays))
                    },
                version = previous.version + 1,
            )
        // Letting a model see confidential data is a sensitive decision: prove a recent login.
        if (next.grantsConsentOver(previous)) workspace.requireRecentAuthentication(clock, auth.reauthMaxAge)
        jdbc.update(
            """
            insert into workspace_settings
              (workspace_id, ai_consent, ai_consent_at, default_visibility, trash_retention_days, export_retention_days, version)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (workspace_id) do update set
              ai_consent = excluded.ai_consent, ai_consent_at = excluded.ai_consent_at,
              default_visibility = excluded.default_visibility,
              trash_retention_days = excluded.trash_retention_days,
              export_retention_days = excluded.export_retention_days, version = excluded.version
            """.trimIndent(),
            workspaceId,
            next.aiConsent.name,
            next.aiConsentAt?.let { OffsetDateTime.ofInstant(it, clock.zone) },
            next.defaultVisibility.name,
            next.retention.trashDays,
            next.retention.exportDays,
            next.version,
        )
        val response = SettingsResponse.from(next)
        audit.record(
            workspace,
            "settings.updated",
            "workspace",
            workspaceId,
            before = SettingsResponse.from(previous),
            after = response,
        )
        return response
    }
}
