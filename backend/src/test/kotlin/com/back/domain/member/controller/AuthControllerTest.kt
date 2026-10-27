package com.back.domain.member.controller

import com.back.domain.member.service.AuthTokenService
import com.back.domain.member.service.MemberService
import com.back.domain.member.social.SocialAuthService
import com.back.global.exception.GlobalExceptionHandler
import com.back.global.exception.UnauthorizedException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

// 서비스는 목으로 두고 AuthController의 요청 검증, 호출 순서, 응답 형태, 오류 변환만 확인한다. (보안 필터와 DB는 통합 테스트 담당)
@ExtendWith(MockKExtension::class)
class AuthControllerTest {

    @MockK
    private lateinit var memberService: MemberService

    @MockK
    private lateinit var authTokenService: AuthTokenService

    @MockK
    private lateinit var socialAuthService: SocialAuthService

    @InjectMockKs
    private lateinit var authController: AuthController

    private lateinit var mockMvc: MockMvc

    companion object {
        private const val MEMBER_ID = 1L
        private const val INVALID_REFRESH_MESSAGE = "유효하지 않은 리프레시 토큰입니다."
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun postJson(path: String, body: String): ResultActions =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun login(body: String): ResultActions = postJson("/api/auth/login", body)

    private fun refresh(body: String): ResultActions = postJson("/api/auth/refresh", body)

    private fun refreshWith(token: String): ResultActions = refresh("""{"refreshToken": "$token"}""")

    @Nested
    @DisplayName("POST /api/auth/login")
    inner class Login {

        @Test
        @DisplayName("인증에 성공하면 액세스 토큰과 DB에 저장된 리프레시 토큰을 Bearer로 돌려준다")
        fun 로그인_성공() {
            every { memberService.authenticate(any()) } returns MEMBER_ID
            every { authTokenService.createAccessToken(MEMBER_ID) } returns "access-token"
            every { memberService.getRefreshToken(MEMBER_ID) } returns "refresh-token"

            login("""{"username": "user-1", "password": "password1234"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
        }

        @Test
        @DisplayName("요청의 아이디와 비밀번호를 그대로 서비스에 넘긴다")
        fun 요청_값을_넘긴다() {
            every { memberService.authenticate(any()) } returns MEMBER_ID
            every { authTokenService.createAccessToken(MEMBER_ID) } returns "access-token"
            every { memberService.getRefreshToken(MEMBER_ID) } returns "refresh-token"

            login("""{"username": "user-1", "password": "password1234"}""").andExpect(status().isOk())

            verify(exactly = 1) {
                memberService.authenticate(match { it.username == "user-1" && it.password == "password1234" })
            }
        }

        @Test
        @DisplayName("인증에 실패하면 401과 서비스 메시지를 돌려주고 토큰을 만들지 않는다")
        fun 인증_실패() {
            every { memberService.authenticate(any()) } throws UnauthorizedException("아이디 또는 비밀번호가 일치하지 않습니다.")

            login("""{"username": "user-1", "password": "wrong"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않습니다."))

            verify(exactly = 0) { authTokenService.createAccessToken(any()) }
            verify(exactly = 0) { memberService.getRefreshToken(any()) }
        }

        @Test
        @DisplayName("저장된 리프레시 토큰이 없으면 401이고 액세스 토큰만 만들어진 채 끝난다")
        fun 리프레시_토큰이_없는_회원() {
            every { memberService.authenticate(any()) } returns MEMBER_ID
            every { authTokenService.createAccessToken(MEMBER_ID) } returns "access-token"
            every { memberService.getRefreshToken(MEMBER_ID) } throws UnauthorizedException("저장된 리프레시 토큰이 없습니다.")

            login("""{"username": "user-1", "password": "password1234"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("저장된 리프레시 토큰이 없습니다."))
        }

        @Test
        @DisplayName("아이디가 없으면 400이고 아이디 필수 메시지만 담긴다")
        fun 아이디_누락() {
            login("""{"password": "password1234"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("아이디는 필수입니다."))

            verify(exactly = 0) { memberService.authenticate(any()) }
        }

        @Test
        @DisplayName("비밀번호가 공백이면 400이고 비밀번호 필수 메시지만 담긴다")
        fun 비밀번호_공백() {
            login("""{"username": "user-1", "password": "   "}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("비밀번호는 필수입니다."))

            verify(exactly = 0) { memberService.authenticate(any()) }
        }

        @Test
        @DisplayName("빈 객체를 보내면 400이고 두 항목 메시지를 모두 돌려준다")
        fun 전부_누락() {
            login("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.containsInAnyOrder("아이디는 필수입니다.", "비밀번호는 필수입니다.")))
        }

        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = ["", "not-json", "{\"username\": ", "[]"])
        @DisplayName("본문이 없거나 JSON 형식이 아니면 400이다")
        fun 본문_형식_오류(body: String) {
            login(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."))

            verify(exactly = 0) { memberService.authenticate(any()) }
        }

        @Test
        @DisplayName("JSON이 아닌 Content-Type이면 415다")
        fun 지원하지_않는_형식() {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("username=a"))
                .andExpect(status().isUnsupportedMediaType())
        }

        @Test
        @DisplayName("GET으로 호출하면 405다")
        fun 허용되지_않는_메서드() {
            mockMvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed())
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    inner class Refresh {

        private fun stubValidRefreshToken(token: String = "refresh-token") {
            every { authTokenService.validateToken(token) } returns true
            every { authTokenService.isRefreshToken(token) } returns true
            every { authTokenService.getMemberId(token) } returns MEMBER_ID
        }

        @Test
        @DisplayName("유효하고 DB 저장값과 같으면 새 액세스 토큰을 주고 리프레시 토큰은 그대로 돌려준다")
        fun 재발급_성공() {
            stubValidRefreshToken()
            every { memberService.matchesRefreshToken(MEMBER_ID, "refresh-token") } returns true
            every { authTokenService.createAccessToken(MEMBER_ID) } returns "new-access-token"

            refreshWith("refresh-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
        }

        @Test
        @DisplayName("서명이나 만료 검증에 실패하면 401이고 이후 검사와 DB 조회를 하지 않는다")
        fun 유효하지_않은_토큰() {
            every { authTokenService.validateToken("invalid-token") } returns false

            refreshWith("invalid-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(INVALID_REFRESH_MESSAGE))

            verify(exactly = 0) { authTokenService.isRefreshToken(any()) }
            verify(exactly = 0) { authTokenService.getMemberId(any()) }
            verify(exactly = 0) { memberService.matchesRefreshToken(any(), any()) }
            verify(exactly = 0) { authTokenService.createAccessToken(any()) }
        }

        @Test
        @DisplayName("액세스 토큰을 리프레시 토큰 자리에 보내면 401이고 DB를 조회하지 않는다")
        fun 액세스_토큰으로_재발급_시도() {
            every { authTokenService.validateToken("access-token") } returns true
            every { authTokenService.isRefreshToken("access-token") } returns false

            refreshWith("access-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_REFRESH_MESSAGE))

            verify(exactly = 0) { authTokenService.getMemberId(any()) }
            verify(exactly = 0) { memberService.matchesRefreshToken(any(), any()) }
            verify(exactly = 0) { authTokenService.createAccessToken(any()) }
        }

        @Test
        @DisplayName("DB 저장값과 다르면 401이고 액세스 토큰을 만들지 않는다")
        fun 저장값과_다른_토큰() {
            stubValidRefreshToken()
            every { memberService.matchesRefreshToken(MEMBER_ID, "refresh-token") } returns false

            refreshWith("refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("저장된 리프레시 토큰과 일치하지 않습니다."))

            verify(exactly = 0) { authTokenService.createAccessToken(any()) }
        }

        @Test
        @DisplayName("토큰 안의 회원이 사라졌으면 서비스의 NoSuchElementException이 404로 나간다 (현재 동작)")
        fun 없는_회원의_토큰() {
            stubValidRefreshToken()
            every { memberService.matchesRefreshToken(MEMBER_ID, "refresh-token") } throws NoSuchElementException("존재하지 않는 회원입니다.")

            refreshWith("refresh-token")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."))
        }

        @ParameterizedTest(name = "refreshToken=\"{0}\"")
        @ValueSource(strings = ["", " ", "   "])
        @DisplayName("토큰이 비어 있거나 공백이면 400이고 토큰 검증을 시도하지 않는다")
        fun 빈_토큰(token: String) {
            refreshWith(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("리프레시 토큰은 필수입니다."))

            verify(exactly = 0) { authTokenService.validateToken(any()) }
        }

        @Test
        @DisplayName("토큰 필드가 없거나 null이면 400이다")
        fun 토큰_누락() {
            refresh("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("리프레시 토큰은 필수입니다."))
            refresh("""{"refreshToken": null}""").andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("본문이 JSON 형식이 아니면 400이다")
        fun 본문_형식_오류() {
            refresh("not-json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."))
        }
    }
}
