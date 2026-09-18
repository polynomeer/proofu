package com.proofu.domain.career

import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ProjectTest {
    private fun project(
        start: LocalDate? = null,
        end: LocalDate? = null,
        teamSize: Int? = null,
        links: List<ProjectLink> = emptyList(),
        careerEntryId: CareerEntryId? = null,
    ) = Project(
        id = ProjectId(Fixtures.uuid(60)),
        workspaceId = Fixtures.workspaceA,
        name = "Checkout redesign",
        role = "Tech lead",
        summary = "Rebuilt the checkout flow",
        careerEntryId = careerEntryId,
        startDate = start,
        endDate = end,
        teamSize = teamSize,
        links = links,
    )

    @Test
    fun `period is only checked when both dates are present`() {
        assertThat(project(start = LocalDate.of(2024, 1, 1)).endDate).isNull()
        assertThat(project(end = LocalDate.of(2024, 1, 1)).startDate).isNull()
        assertThatThrownBy { project(start = LocalDate.of(2024, 2, 1), end = LocalDate.of(2024, 1, 1)) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `team size must be positive when given`() {
        assertThatThrownBy { project(teamSize = 0) }.isInstanceOf(DomainRuleViolation::class.java)
        assertThat(project(teamSize = 4).teamSize).isEqualTo(4)
    }

    @Test
    fun `links need a label and an absolute http url`() {
        assertThatThrownBy { ProjectLink("", "https://example.com") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { ProjectLink("Repo", "example.com/repo") }.isInstanceOf(DomainRuleViolation::class.java)
        assertThat(ProjectLink("Repo", "https://example.com/repo").url).startsWith("https://")
    }

    @Test
    fun `at most twenty links`() {
        val links = List(Project.MAX_LINKS + 1) { ProjectLink("l$it", "https://example.com/$it") }
        assertThatThrownBy { project(links = links) }.isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `attachment requires the same entry and workspace`() {
        val entryId = CareerEntryId(Fixtures.uuid(61))
        val entry =
            CareerEntry(
                id = entryId,
                workspaceId = Fixtures.workspaceA,
                type = CareerEntryType.EMPLOYMENT,
                title = "Engineer",
                startDate = LocalDate.of(2020, 1, 1),
            )
        assertThat(project(careerEntryId = entryId).attachedTo(entry)).isTrue()
        assertThat(project(careerEntryId = entryId).attachedTo(entry.copy(workspaceId = Fixtures.workspaceB))).isFalse()
        assertThat(project().attachedTo(entry)).isFalse()
    }
}
