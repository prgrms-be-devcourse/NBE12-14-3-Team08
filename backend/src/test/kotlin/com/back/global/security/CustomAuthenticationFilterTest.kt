package com.back.global.security

import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

// 인증 필터의 헤더 파싱과 인증 정보 설정 규칙을 스프링 없이 확인한다.
// 필터는 인증에 실패해도 요청을 막지 않고 그냥 넘기며, 401은 뒤쪽 보안 설정(EntryPoint)이 만든다.
@ExtendWith(MockKExtension::class)
class CustomAuthenticationFilterTest {

    @MockK
    private lateinit var authTokenService: AuthTokenService

    @MockK
    private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var filter: CustomAuthenticationFilter

    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var chain: MockFilterChain

    companion object {
        private const val MEMBER_ID = 7L
    }

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        request = MockHttpServletRequest("GET", "/api/members/me")
        response = MockHttpServletResponse()
        chain = MockFilterChain()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun run(authorization: String? = null) {
        if (authorization != null) request.addHeader("Authorization", authorization)
        filter.doFilter(request, response, chain)
    }

    private fun stubValidAccessToken(token: String = "access-token") {
        every { authTokenService.validateToken(token) } returns true
        every { authTokenService.isAccessToken(token) } returns true
        every { authTokenService.getMemberId(token) } returns MEMBER_ID
    }

    private fun assertNotAuthenticated() {
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(request.getAttribute("loginMemberId")).isNull()
    }

    // 인증에 성공하든 실패하든 필터는 요청을 다음 단계로 넘기고, 직접 응답을 만들지 않는다.
    private fun assertPassedThrough() {
        assertThat(chain.request).isSameAs(request)
        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentAsString).isEmpty()
    }

    @Nested
    @DisplayName("인증 성공")
    inner class Success {

        @Test
        @DisplayName("유효한 액세스 토큰이고 ACTIVE 회원이면 회원 id가 요청 속성과 SecurityContext에 담긴다")
        fun 인증_성공() {
            stubValidAccessToken()
            every { memberRepository.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE) } returns true

            run("Bearer access-token")

            val authentication = SecurityContextHolder.getContext().authentication
            assertThat(request.getAttribute("loginMemberId")).isEqualTo(MEMBER_ID)
            assertThat(authentication).isNotNull()
            assertThat(authentication!!.principal).isEqualTo(MEMBER_ID)
            assertThat(authentication.isAuthenticated).isTrue()
            assertThat(authentication.authorities).isEmpty()
            assertPassedThrough()
        }

        @Test
        @DisplayName("회원 상태는 토큰 속 id로 DB에서 확인한다")
        fun 회원_상태를_확인한다() {
            stubValidAccessToken()
            every { memberRepository.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE) } returns true

            run("Bearer access-token")

            verify(exactly = 1) { memberRepository.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE) }
        }
    }

    @Nested
    @DisplayName("헤더가 없거나 형식이 다를 때")
    inner class HeaderFormat {

        @Test
        @DisplayName("Authorization 헤더가 없으면 토큰 검증도 DB 조회도 하지 않고 넘긴다")
        fun 헤더_없음() {
            run()

            assertNotAuthenticated()
            assertPassedThrough()
        }

        @ParameterizedTest(name = "Authorization=\"{0}\"")
        @ValueSource(strings = ["Basic dXNlcjpwYXNz", "bearer access-token", "BEARER access-token", "Bearer", "Bearer\taccess-token", "Token access-token", "access-token", ""])
        @DisplayName("Bearer + 공백으로 시작하지 않으면 토큰으로 보지 않는다 (접두어는 대소문자를 구분한다)")
        fun Bearer가_아닌_헤더(header: String) {
            run(header)

            assertNotAuthenticated()
            assertPassedThrough()
        }

        @Test
        @DisplayName("Bearer 뒤가 비어 있으면 빈 토큰으로 검증해서 거절한다")
        fun 빈_토큰() {
            every { authTokenService.validateToken("") } returns false

            run("Bearer ")

            assertNotAuthenticated()
            assertPassedThrough()
            verify(exactly = 0) { authTokenService.isAccessToken(any()) }
        }

        @Test
        @DisplayName("Bearer 뒤에 공백이 두 칸이면 앞쪽 공백을 자르지 않고 그대로 검증한다 (현재 동작)")
        fun 공백이_두_칸() {
            every { authTokenService.validateToken(" access-token") } returns false

            run("Bearer  access-token")

            assertNotAuthenticated()
            verify(exactly = 1) { authTokenService.validateToken(" access-token") }
            verify(exactly = 0) { authTokenService.validateToken("access-token") }
        }
    }

    @Nested
    @DisplayName("토큰이 인정되지 않을 때")
    inner class Rejected {

        @Test
        @DisplayName("서명, 만료 검증에 실패하면 이후 검사를 하지 않고 인증 없이 넘긴다")
        fun 유효하지_않은_토큰() {
            every { authTokenService.validateToken("bad-token") } returns false

            run("Bearer bad-token")

            assertNotAuthenticated()
            assertPassedThrough()
            verify(exactly = 0) { authTokenService.isAccessToken(any()) }
            verify(exactly = 0) { authTokenService.getMemberId(any()) }
            verify(exactly = 0) { memberRepository.existsByIdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("리프레시 토큰이면 회원 id를 읽거나 DB를 조회하지 않고 인증 없이 넘긴다")
        fun 리프레시_토큰() {
            every { authTokenService.validateToken("refresh-token") } returns true
            every { authTokenService.isAccessToken("refresh-token") } returns false

            run("Bearer refresh-token")

            assertNotAuthenticated()
            assertPassedThrough()
            verify(exactly = 0) { authTokenService.getMemberId(any()) }
            verify(exactly = 0) { memberRepository.existsByIdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("ACTIVE가 아닌 회원(탈퇴, 없는 회원)이면 인증 정보를 담지 않고 넘긴다")
        fun 활성이_아닌_회원() {
            stubValidAccessToken()
            every { memberRepository.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE) } returns false

            run("Bearer access-token")

            assertNotAuthenticated()
            assertPassedThrough()
        }
    }

    @Nested
    @DisplayName("요청 간 독립성")
    inner class Isolation {

        @Test
        @DisplayName("요청 속성의 loginMemberId는 필터가 설정한 값만 쓴다 (클라이언트가 속성을 심을 수는 없지만, 실패한 요청에는 남아 있지 않아야 한다)")
        fun 실패한_요청에는_속성이_없다() {
            every { authTokenService.validateToken("bad-token") } returns false

            run("Bearer bad-token")

            assertThat(request.getAttribute("loginMemberId")).isNull()
        }

        @Test
        @DisplayName("같은 필터로 성공한 뒤 실패한 요청을 처리하면 두 번째 요청은 인증되지 않는다")
        fun 이전_요청의_인증이_넘어오지_않는다() {
            stubValidAccessToken()
            every { memberRepository.existsByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE) } returns true
            run("Bearer access-token")
            assertThat(request.getAttribute("loginMemberId")).isEqualTo(MEMBER_ID)

            // 실제 서버에서는 요청이 끝나면 SecurityContextHolderFilter가 컨텍스트를 비운다. 그 뒤의 새 요청을 흉내 낸다.
            SecurityContextHolder.clearContext()
            val next = MockHttpServletRequest("GET", "/api/members/me")
            every { authTokenService.validateToken("bad-token") } returns false
            next.addHeader("Authorization", "Bearer bad-token")
            filter.doFilter(next, MockHttpServletResponse(), MockFilterChain())

            assertThat(SecurityContextHolder.getContext().authentication).isNull()
            assertThat(next.getAttribute("loginMemberId")).isNull()
        }
    }
}
