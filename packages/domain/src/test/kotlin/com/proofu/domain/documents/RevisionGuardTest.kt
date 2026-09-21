package com.proofu.domain.documents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RevisionGuardTest {
    private val original = "온보딩 플로우를 5단계에서 2단계로 축소해 활성화율을 40% 개선했습니다."

    @Test
    fun `accepts rewrites that keep the numbers of the original or its facts`() {
        assertThat(RevisionGuard.check(original, "온보딩을 5→2단계로 줄여 활성화율 40%를 높였습니다.")).isEmpty()
        assertThat(
            RevisionGuard.check(original, "5→2단계 축소로 7일 활성화율 40% 개선", facts = listOf("가입 후 7일 활성화율 상승")),
        ).isEmpty()
    }

    @Test
    fun `refuses new numbers, empty text and runaway length`() {
        assertThat(RevisionGuard.check(original, "활성화율을 45% 개선했습니다.")).anyMatch { it.contains("[45]") }
        assertThat(RevisionGuard.check(original, "온보딩을 2단계로 축소해 활성화율을 40% 개선했습니다.")).anyMatch {
            it.contains("drops") &&
                it.contains("5")
        }
        assertThat(RevisionGuard.check(original, "   ")).contains("empty revision")
        assertThat(RevisionGuard.check("짧다", "아".repeat(200))).anyMatch { it.contains("longer") }
        assertThat(
            RevisionGuard.numbers("1,200명, 2024.03, 40%"),
        ).containsExactly("1200", "1", "200", "202403", "2024", "3", "40")
    }

    @Test
    fun `reformatted dates and thousands are not new numbers`() {
        assertThat(RevisionGuard.check("2024.03부터 2024-12까지 1,200명", "2024년 3월부터 12월까지 1200명")).isEmpty()
        assertThat(RevisionGuard.check("2019.04 입사", "2019년 4월 입사 (경력 6년)")).anyMatch { it.contains("6") }
    }
}
