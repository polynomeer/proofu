package com.proofu.domain.career

import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CareerEntryTest {
    private fun entry(
        start: LocalDate,
        end: LocalDate?,
    ) = CareerEntry(
        id = CareerEntryId(Fixtures.uuid(10)),
        workspaceId = Fixtures.workspaceA,
        type = CareerEntryType.EMPLOYMENT,
        title = "Backend Engineer",
        startDate = start,
        endDate = end,
    )

    @Test
    fun `end date must not precede start date`() {
        assertThatThrownBy { entry(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `open ended entries are ongoing`() {
        assertThat(entry(LocalDate.of(2024, 3, 1), null).isOngoing).isTrue()
        assertThat(entry(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 1)).isOngoing).isFalse()
    }
}
