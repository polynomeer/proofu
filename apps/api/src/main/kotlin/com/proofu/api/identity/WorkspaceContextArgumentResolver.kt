package com.proofu.api.identity

import com.proofu.api.web.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Lets controllers declare a `WorkspaceContext` parameter; unauthenticated calls get 401. */
@Component
class WorkspaceContextArgumentResolver(
    private val resolver: WorkspaceResolver,
) : HandlerMethodArgumentResolver,
    WebMvcConfigurer {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == WorkspaceContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): WorkspaceContext {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java) ?: throw unauthenticated()
        return resolver.resolve(request) ?: throw unauthenticated()
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }

    private fun unauthenticated(): ErrorResponseException {
        val code = ErrorCode.UNAUTHENTICATED
        val body = ProblemDetail.forStatusAndDetail(code.status, "Authentication required")
        body.setProperty("code", code.name)
        return ErrorResponseException(code.status, body, null)
    }
}
