package com.proofu.api.web

import com.proofu.ai.AiBudgetExceeded
import com.proofu.ai.AiCallFailed
import com.proofu.ai.BudgetBlock
import com.proofu.ai.model.ModelProviderException
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.ImmutableSnapshotViolation
import com.proofu.domain.common.InvalidStatusTransition
import com.proofu.domain.common.UnapprovedBlocksInExport
import com.proofu.domain.common.WorkspaceBoundaryViolation
import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

/**
 * Maps every failure to RFC 9457 Problem Details with a stable `code` and, for
 * validation failures, a `fieldErrors` list. Internal details never leak: the
 * detail text for unexpected errors is generic and the cause is logged with the
 * request id instead.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(WorkspaceBoundaryViolation::class)
    fun onWorkspaceBoundary(e: WorkspaceBoundaryViolation): ProblemDetail =
        // Cross-workspace resources are reported as absent, not forbidden, to avoid leaking existence.
        problem(ErrorCode.NOT_FOUND, "Resource not found")

    @ExceptionHandler(InvalidStatusTransition::class)
    fun onInvalidTransition(e: InvalidStatusTransition): ProblemDetail =
        problem(ErrorCode.INVALID_STATUS_TRANSITION, e.message)

    @ExceptionHandler(ImmutableSnapshotViolation::class)
    fun onImmutableSnapshot(e: ImmutableSnapshotViolation): ProblemDetail =
        problem(ErrorCode.SNAPSHOT_IMMUTABLE, e.message)

    @ExceptionHandler(UnapprovedBlocksInExport::class)
    fun onUnapprovedBlocks(e: UnapprovedBlocksInExport): ProblemDetail =
        problem(
            ErrorCode.UNSUPPORTED_CLAIM_IN_EXPORT,
            "승인되지 않은 근거 없음·추론 문장이 있어 제출할 수 없습니다: ${e.blockIds.joinToString(", ")}",
        )

    @ExceptionHandler(DomainRuleViolation::class)
    fun onDomainRule(e: DomainRuleViolation): ProblemDetail = problem(ErrorCode.DOMAIN_RULE_VIOLATION, e.message)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun onNotFound(e: ResourceNotFoundException): ProblemDetail = problem(ErrorCode.NOT_FOUND, e.message)

    @ExceptionHandler(StaleVersionException::class)
    fun onStaleVersion(e: StaleVersionException): ProblemDetail = problem(ErrorCode.CONFLICT_STALE_VERSION, e.message)

    @ExceptionHandler(InvalidRequestException::class)
    fun onInvalidRequest(e: InvalidRequestException): ProblemDetail =
        problem(ErrorCode.VALIDATION_FAILED, e.message).apply {
            if (e.fieldErrors.isNotEmpty()) {
                setProperty(
                    "fieldErrors",
                    e.fieldErrors.map { (field, message) ->
                        mapOf(
                            "field" to field,
                            "message" to message,
                        )
                    },
                )
            }
        }

    @ExceptionHandler(AiBudgetExceeded::class)
    fun onAiBudget(e: AiBudgetExceeded): ProblemDetail =
        problem(
            ErrorCode.AI_BUDGET_EXCEEDED,
            when (e.block) {
                BudgetBlock.WORKSPACE_MONTHLY_BUDGET -> "이번 달 AI 예산을 모두 사용했습니다. 다음 달에 다시 시도하세요."
                BudgetBlock.WORKSPACE_HOURLY_LIMIT -> "시간당 AI 작업 한도에 도달했습니다. 잠시 후 다시 시도하세요."
                BudgetBlock.DEPLOYMENT_DAILY_BUDGET -> "서비스 전체 AI 예산이 오늘 소진되었습니다. 내일 다시 시도하세요."
            },
        )

    @ExceptionHandler(AiCallFailed.Refused::class)
    fun onAiRefused(e: AiCallFailed.Refused): ProblemDetail =
        problem(ErrorCode.AI_REFUSED, "AI 제공자가 이 요청을 처리하지 않았습니다. 입력 내용을 확인하세요.")

    @ExceptionHandler(AiCallFailed.InvalidOutput::class)
    fun onAiInvalidOutput(e: AiCallFailed.InvalidOutput): ProblemDetail =
        problem(ErrorCode.AI_OUTPUT_INVALID, "AI 응답이 검증을 통과하지 못해 채택하지 않았습니다. 다시 시도하세요.")

    @ExceptionHandler(ModelProviderException::class)
    fun onAiProvider(e: ModelProviderException): ProblemDetail =
        problem(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI 제공자에 연결하지 못했습니다. 잠시 후 다시 시도하세요.")

    @ExceptionHandler(NotImplementedException::class)
    fun onNotImplemented(e: NotImplementedException): ProblemDetail = problem(ErrorCode.NOT_IMPLEMENTED, e.message)

    @ExceptionHandler(Exception::class)
    fun onUnexpected(
        e: Exception,
        request: WebRequest,
    ): ProblemDetail {
        if (e is ErrorResponseException) {
            return e.body.also { it.setProperty(CODE, codeFor(it.status)) }
        }
        log.error("Unhandled exception for {}", request.getDescription(false), e)
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred")
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed")
        body.setProperty(
            "fieldErrors",
            ex.bindingResult.fieldErrors.map {
                mapOf(
                    "field" to it.field,
                    "message" to (it.defaultMessage ?: "invalid"),
                    "rejectedValue" to it.rejectedValue,
                )
            },
        )
        return ResponseEntity.status(status).headers(headers).body(body)
    }

    /** Every framework-produced ProblemDetail (404, 405, 415, ...) also carries a `code`. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        // The framework passes a null body for ErrorResponse exceptions and builds it later;
        // resolve it here so the code property can be attached.
        val resolved =
            body ?: (ex as? ErrorResponse)?.updateAndGetBody(messageSource, LocaleContextHolder.getLocale())
        if (resolved is ProblemDetail) {
            if (resolved.properties?.containsKey(CODE) != true) resolved.setProperty(CODE, codeFor(statusCode))
            // Spring's default wording ("No static resource ...") is misleading for an API.
            if (ex is NoResourceFoundException) resolved.detail = "No route matches ${ex.resourcePath}"
        }
        return super.handleExceptionInternal(ex, resolved, headers, statusCode, request)
    }

    override fun createProblemDetail(
        ex: Exception,
        status: HttpStatusCode,
        defaultDetail: String,
        detailMessageCode: String?,
        detailMessageArguments: Array<out Any>?,
        request: WebRequest,
    ): ProblemDetail =
        super
            .createProblemDetail(ex, status, defaultDetail, detailMessageCode, detailMessageArguments, request)
            .also { it.setProperty(CODE, codeFor(status)) }

    private fun problem(
        code: ErrorCode,
        detail: String?,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(code.status, detail ?: code.status.reasonPhrase).apply {
            type = URI.create("$TYPE_BASE/${code.name.lowercase().replace('_', '-')}")
            title = code.status.reasonPhrase
            setProperty(CODE, code.name)
        }

    private fun codeFor(status: Int): String =
        when (status) {
            400 -> ErrorCode.VALIDATION_FAILED
            401 -> ErrorCode.UNAUTHENTICATED
            403 -> ErrorCode.FORBIDDEN_WORKSPACE
            404 -> ErrorCode.NOT_FOUND
            409 -> ErrorCode.CONFLICT_STALE_VERSION
            429 -> ErrorCode.RATE_LIMITED
            else -> ErrorCode.INTERNAL_ERROR
        }.name

    private fun codeFor(status: HttpStatusCode): String = codeFor(status.value())

    private companion object {
        const val CODE = "code"
        const val TYPE_BASE = "https://proofu.dev/problems"
    }
}
