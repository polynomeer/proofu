package com.proofu.ai.model

import com.proofu.domain.common.Sensitivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FakeModelClientTest {
    @Test
    fun `heuristic extraction turns bullets under known headers into items`() {
        val request =
            ModelRequest(
                model = "fake",
                purpose = AiPurpose.REQUIREMENT_EXTRACTION,
                systemPrompt = "",
                documents =
                    listOf(
                        ContextDocument(
                            "snapshot:1",
                            "t",
                            "[자격 요건]\n- 경력 5년\n[우대 사항]\n- 영어\n[복지]\n- 점심 식대",
                            Sensitivity.INTERNAL,
                        ),
                    ),
                instruction = "",
                outputSchema = "{}",
                effort = Effort.LOW,
                maxOutputTokens = 100,
            )
        val outcome = FakeModelClient().complete(request) as ModelOutcome.Completed
        assertThat(outcome.text)
            .contains("\"REQUIRED\"")
            .contains("경력 5년")
            .contains("\"PREFERRED\"")
            .contains("영어")
        assertThat(outcome.text).doesNotContain("점심 식대")
    }
}
