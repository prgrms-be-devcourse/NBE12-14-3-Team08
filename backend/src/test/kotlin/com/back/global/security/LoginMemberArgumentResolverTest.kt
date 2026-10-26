package com.back.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.ServletWebRequest

// @LoginMemberId 파라미터를 지원하는 조건과, 필터가 심어 둔 회원 id를 꺼내는 동작을 확인한다.
class LoginMemberArgumentResolverTest {

    private val resolver = LoginMemberArgumentResolver()

    // 파라미터 모양별로 하나씩 만든 가짜 컨트롤러. 메서드 이름으로 MethodParameter를 얻는다.
    @Suppress("unused", "UNUSED_PARAMETER")
    private class Sample {
        fun primitiveLong(@LoginMemberId memberId: Long) {}
        fun boxedLong(@LoginMemberId memberId: Long?) {}
        fun annotatedString(@LoginMemberId memberId: String) {}
        fun annotatedInt(@LoginMemberId memberId: Int) {}
        fun plainLong(memberId: Long) {}
    }

    private fun parameter(method: String): MethodParameter {
        val target = Sample::class.java.declaredMethods.first { it.name == method }
        return MethodParameter(target, 0)
    }

    private fun requestWith(attributeValue: Any?): ServletWebRequest {
        val servletRequest = MockHttpServletRequest()
        if (attributeValue != null) servletRequest.setAttribute("loginMemberId", attributeValue)
        return ServletWebRequest(servletRequest)
    }

    @Nested
    @DisplayName("supportsParameter")
    inner class Supports {

        @Test
        @DisplayName("@LoginMemberId가 붙은 Long(원시형 long)을 지원한다 (Kotlin 컨트롤러)")
        fun 원시형_long() {
            assertThat(resolver.supportsParameter(parameter("primitiveLong"))).isTrue()
        }

        @Test
        @DisplayName("@LoginMemberId가 붙은 Long?(박싱형)을 지원한다 (Java 컨트롤러)")
        fun 박싱형_long() {
            assertThat(resolver.supportsParameter(parameter("boxedLong"))).isTrue()
        }

        @Test
        @DisplayName("어노테이션이 없으면 Long이어도 지원하지 않는다")
        fun 어노테이션이_없다() {
            assertThat(resolver.supportsParameter(parameter("plainLong"))).isFalse()
        }

        @Test
        @DisplayName("어노테이션이 있어도 String이나 Int는 지원하지 않는다")
        fun 다른_타입() {
            assertThat(resolver.supportsParameter(parameter("annotatedString"))).isFalse()
            assertThat(resolver.supportsParameter(parameter("annotatedInt"))).isFalse()
        }
    }

    @Nested
    @DisplayName("resolveArgument")
    inner class Resolve {

        @Test
        @DisplayName("요청 속성 loginMemberId의 값을 그대로 돌려준다")
        fun 속성_값() {
            val resolved = resolver.resolveArgument(parameter("primitiveLong"), null, requestWith(7L), null)

            assertThat(resolved).isEqualTo(7L)
        }

        @Test
        @DisplayName("속성이 없으면 null을 돌려준다 (인증 필터를 거치지 않은 요청이라는 뜻)")
        fun 속성_없음() {
            val resolved = resolver.resolveArgument(parameter("primitiveLong"), null, requestWith(null), null)

            assertThat(resolved).isNull()
        }

        @Test
        @DisplayName("세션 같은 다른 범위에 같은 이름이 있어도 요청 범위 값만 쓴다")
        fun 요청_범위만_읽는다() {
            val servletRequest = MockHttpServletRequest()
            val webRequest = ServletWebRequest(servletRequest)
            webRequest.setAttribute("loginMemberId", 99L, NativeWebRequest.SCOPE_SESSION)

            val resolved = resolver.resolveArgument(parameter("primitiveLong"), null, webRequest, null)

            assertThat(resolved).isNull()
        }

        @Test
        @DisplayName("요청 파라미터나 헤더에 loginMemberId를 실어 보내도 무시한다 (회원 id를 위조할 수 없다)")
        fun 요청_파라미터는_무시한다() {
            val servletRequest = MockHttpServletRequest()
            servletRequest.addParameter("loginMemberId", "99")
            servletRequest.addHeader("loginMemberId", "99")

            val resolved = resolver.resolveArgument(parameter("primitiveLong"), null, ServletWebRequest(servletRequest), null)

            assertThat(resolved).isNull()
        }
    }
}
