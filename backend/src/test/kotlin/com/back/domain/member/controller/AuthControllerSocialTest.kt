package com.back.domain.member.controller

import com.back.domain.member.service.AuthTokenService
import com.back.domain.member.service.MemberService
import com.back.domain.member.social.SocialAuthService
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.GlobalExceptionHandler
import com.back.global.exception.SocialAuthUnavailableException
import com.back.global.exception.UnauthorizedException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

// POST /api/auth/social/{provider}의 요청 검증, 호출 순서, 응답 형태, 오류 변환을 확인한다. (보안 필터와 DB는 통합 테스트 담당)
@ExtendWith(MockKExtension::class)
class AuthControllerSocialTest {

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
        private const val BODY = """{"code": "auth-code", "redirectUri": "http://localhost:3000/auth/callback/google"}"""
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun socialLogin(provider: String, body: String): ResultActions =
        mockMvc.perform(post("/api/auth/social/$provider").contentType(MediaType.APPLICATION_JSON).content(body))

    @Test
    @DisplayName("성공하면 일반 로그인과 같은 형태로 액세스 토큰과 DB에 저장된 리프레시 토큰을 Bearer로 돌려준다")
    fun 소셜_로그인_성공() {
        every { socialAuthService.login("google", "auth-code", "http://localhost:3000/auth/callback/google") } returns MEMBER_ID
        every { authTokenService.createAccessToken(MEMBER_ID) } returns "access-token"
        every { memberService.getRefreshToken(MEMBER_ID) } returns "refresh-token"

        socialLogin("google", BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    @DisplayName("코드나 리다이렉트 주소가 비어 있으면 400이고 서비스를 부르지 않는다")
    fun 필수값_누락() {
        socialLogin("google", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(2))
        socialLogin("google", """{"code": " ", "redirectUri": "http://localhost:3000/cb"}""")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("인가 코드는 필수입니다."))

        verify(exactly = 0) { socialAuthService.login(any(), any(), any()) }
    }

    @Test
    @DisplayName("지원하지 않는 공급자면 400이다")
    fun 지원하지_않는_공급자() {
        every { socialAuthService.login("naver", any(), any()) } throws BusinessRuleException("지원하지 않는 로그인 방식입니다.")

        socialLogin("naver", BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지원하지 않는 로그인 방식입니다."))
    }

    @Test
    @DisplayName("코드가 거절되면 401이고 토큰을 발급하지 않는다")
    fun 코드_거절() {
        every { socialAuthService.login(any(), any(), any()) } throws UnauthorizedException("구글 로그인에 실패했습니다. 다시 시도해주세요.")

        socialLogin("google", BODY)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("구글 로그인에 실패했습니다. 다시 시도해주세요."))

        verify(exactly = 0) { authTokenService.createAccessToken(any()) }
    }

    @Test
    @DisplayName("공급자와 통신하지 못하거나 설정이 없으면 502다")
    fun 공급자_장애() {
        every { socialAuthService.login(any(), any(), any()) } throws SocialAuthUnavailableException("구글 로그인이 설정되지 않았습니다.")

        socialLogin("google", BODY)
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message").value("구글 로그인이 설정되지 않았습니다."))
    }

    @Test
    @DisplayName("JSON이 아닌 본문이면 400이다")
    fun 본문_형식_오류() {
        socialLogin("google", "not-json")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."))
    }
}
