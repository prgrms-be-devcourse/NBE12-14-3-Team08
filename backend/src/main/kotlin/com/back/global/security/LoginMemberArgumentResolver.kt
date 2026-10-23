package com.back.global.security

import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoginMemberArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        val hasAnnotation = parameter.hasParameterAnnotation(LoginMemberId::class.java)

        // Java 컨트롤러의 Long(박싱형)과 Kotlin 컨트롤러의 Long(원시형 long) 둘 다 지원한다.
        val parameterType = parameter.parameterType
        val isLongType = parameterType == Long::class.javaObjectType ||
            parameterType == Long::class.javaPrimitiveType

        return hasAnnotation && isLongType
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? = webRequest.getAttribute("loginMemberId", NativeWebRequest.SCOPE_REQUEST)
}
