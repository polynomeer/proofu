package com.proofu.ai.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResumeRedactorTest {
    @Test
    fun `the details a resume opens with never reach the model`() {
        val result =
            ResumeRedactor.redact(
                """
                홍길동
                gildong.hong@example.com | 010-1234-5678 | 서울
                주민등록번호 900101-1234567
                """.trimIndent(),
            )
        assertThat(result.text).doesNotContain("gildong.hong@example.com", "010-1234-5678", "900101")
        assertThat(result.text).contains("홍길동", "서울")
        assertThat(result.count).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `what a career record is made of survives`() {
        val text =
            """
            2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어
            2017.03-2020.02 다른회사 / 주니어 개발자
            결제 시스템을 재작성해 응답 시간을 420ms에서 180ms로 줄였습니다.
            팀 8명, 하루 1,200만 건 처리.
            """.trimIndent()
        val result = ResumeRedactor.redact(text)
        assertThat(result.text).isEqualTo(text)
        assertThat(result.count).isZero()
    }

    @Test
    fun `an international number and a bare digit run are both contact details`() {
        assertThat(ResumeRedactor.redact("+82 10 1234 5678").text).isEqualTo(ResumeRedactor.MASK)
        assertThat(ResumeRedactor.redact("01012345678").text).isEqualTo(ResumeRedactor.MASK)
    }
}
