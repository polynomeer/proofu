package com.proofu.renderer

import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/** Writes a sample PDF for eyeballing; only when PDF_SAMPLE_DIR is set. */
class PdfSampleWriter {
    @Test
    @EnabledIfEnvironmentVariable(named = "PDF_SAMPLE_DIR", matches = ".+")
    fun `write a sample`() {
        val output =
            GeneratedOutput(
                listOf(
                    GeneratedBlock(
                        "motivation-1",
                        "B2B SaaS 제품을 데이터로 개선하는 일에 지원합니다. 온보딩과 활성화 지표를 다뤄 왔습니다.",
                        certainty = Certainty.UNSUPPORTED,
                        approvedByUser = true,
                    ),
                    GeneratedBlock(
                        "experience-1",
                        "온보딩 플로우를 5단계에서 2단계로 축소해 가입 후 7일 활성화율을 40% 개선했습니다.\n지표 정의와 실험 설계를 직접 맡았습니다.",
                        certainty = Certainty.SUPPORTED,
                    ),
                    GeneratedBlock(
                        "contribution-1",
                        "기록된 사실 범위에서 로드맵 수립과 데이터 기반 의사결정에 기여하겠습니다. English text and numbers 12,345 render too.",
                        certainty = Certainty.INFERRED,
                        approvedByUser = true,
                    ),
                ),
            )
        val doc =
            RenderableDocument.of(
                "자기소개서",
                DocumentType.COVER_LETTER,
                "ko",
                "예시 주식회사",
                "프로덕트 매니저",
                DocumentTemplate.latest(DocumentType.COVER_LETTER),
                output,
            )
        File(System.getenv("PDF_SAMPLE_DIR"), "sample.pdf").writeBytes(PdfRenderer().render(doc).bytes)
    }
}
