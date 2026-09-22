package com.proofu.domain.identity

import com.proofu.domain.common.AiConsent
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.Sensitivity
import com.proofu.domain.common.WorkspaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class WorkspaceSettingsTest {
    private val ws = WorkspaceId(Fixtures.uuid(1))

    @Test
    fun `consent unlocks confidential data only and never restricted`() {
        assertThat(Sensitivity.CONFIDENTIAL.allowedInAiContext(AiConsent.NONE)).isFalse()
        assertThat(Sensitivity.CONFIDENTIAL.allowedInAiContext(AiConsent.CONFIDENTIAL)).isTrue()
        assertThat(Sensitivity.RESTRICTED.allowedInAiContext(AiConsent.CONFIDENTIAL)).isFalse()
        assertThat(Sensitivity.INTERNAL.allowedInAiContext(AiConsent.NONE)).isTrue()
        assertThat(Sensitivity.CONFIDENTIAL.allowedInAiContextByDefault).isFalse()
    }

    @Test
    fun `consent carries its timestamp and granting is the sensitive direction`() {
        val none = WorkspaceSettings(ws)
        val granted = WorkspaceSettings(ws, AiConsent.CONFIDENTIAL, Instant.parse("2026-09-22T00:00:00Z"))
        assertThat(granted.grantsConsentOver(none)).isTrue()
        assertThat(none.grantsConsentOver(granted)).isFalse()
        assertThatThrownBy {
            WorkspaceSettings(
                ws,
                AiConsent.CONFIDENTIAL,
                null,
            )
        }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy {
            WorkspaceSettings(ws, AiConsent.NONE, Instant.now())
        }.isInstanceOf(DomainRuleViolation::class.java)
    }
}
