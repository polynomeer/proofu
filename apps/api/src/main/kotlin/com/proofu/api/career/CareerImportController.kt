package com.proofu.api.career

import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.AcceptedJob
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ApiPaths
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Matches `CareerImportRequest` in packages/contracts/openapi.yaml. */
data class CareerImportRequest(
    @field:NotBlank
    @field:Size(min = 10, max = 50_000, message = "{min}자 이상 {max}자 이하로 입력하세요")
    val text: String?,
)

/**
 * F13: hands a pasted resume to the `career.import` job. The proposals come back in the job
 * result and nothing is stored until the user creates the entries they picked — so this
 * endpoint writes no career data and the text lives only in the job row
 * (docs/ai/ai-feature-spec.md §이력서 가져오기).
 */
@RestController
@RequestMapping(ApiPaths.V1)
class CareerImportController(
    private val jobs: JobService,
) {
    @PostMapping("/career-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun import(
        workspace: WorkspaceContext,
        @Valid @RequestBody request: CareerImportRequest,
    ): AcceptedJob =
        AcceptedJob(
            jobs.enqueue(
                workspace,
                type = JobTypes.CAREER_IMPORT,
                payload = mapOf("text" to requireNotNull(request.text)),
            ),
        )
}
