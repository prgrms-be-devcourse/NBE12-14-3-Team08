package com.back.domain.member.social

import com.back.domain.member.entity.MemberProvider
import com.back.global.exception.SocialAuthUnavailableException
import com.back.global.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException

// 구글 서버는 MockRestServiceServer로 대신하고, 요청을 올바르게 만들고 응답·오류를 우리 예외로 바꾸는지 확인한다.
class GoogleOAuthClientTest {

    companion object {
        private const val TOKEN_URI = "https://google.test/token"
        private const val USER_INFO_URI = "https://google.test/userinfo"
        private const val REDIRECT_URI = "http://localhost:3000/auth/callback/google"
        private const val LOGIN_FAILED_MESSAGE = "구글 로그인에 실패했습니다. 다시 시도해주세요."
    }

    private lateinit var server: MockRestServiceServer
    private lateinit var restClient: RestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
    }

    private fun client(clientId: String = "client-id", clientSecret: String = "client-secret") =
        GoogleOAuthClient(restClient, clientId, clientSecret, TOKEN_URI, USER_INFO_URI)

    private fun expectTokenSuccess() {
        server.expect(requestTo(TOKEN_URI))
            .andRespond(withSuccess("""{"access_token": "google-access-token", "token_type": "Bearer"}""", MediaType.APPLICATION_JSON))
    }

    @Test
    @DisplayName("공급자는 GOOGLE이다")
    fun 공급자() {
        assertThat(client().provider).isEqualTo(MemberProvider.GOOGLE)
    }

    @Test
    @DisplayName("코드를 토큰으로 바꾼 뒤 그 토큰으로 조회한 sub와 이름을 돌려준다")
    fun 프로필_조회_성공() {
        expectTokenSuccess()
        server.expect(requestTo(USER_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer google-access-token"))
            .andRespond(withSuccess("""{"sub": "1234567890", "name": "홍길동", "email": "a@b.c"}""", MediaType.APPLICATION_JSON))

        val profile = client().fetchProfile("auth-code", REDIRECT_URI)

        assertThat(profile.providerId).isEqualTo("1234567890")
        assertThat(profile.nickname).isEqualTo("홍길동")
        server.verify()
    }

    @Test
    @DisplayName("토큰 교환 요청에는 코드·리다이렉트 주소·클라이언트 정보가 폼으로 담긴다")
    fun 토큰_요청_내용() {
        server.expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(
                content().formDataContains(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to "auth-code",
                        "redirect_uri" to REDIRECT_URI,
                        "client_id" to "client-id",
                        "client_secret" to "client-secret",
                    ),
                ),
            )
            .andRespond(withSuccess("""{"access_token": "t"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("""{"sub": "1"}""", MediaType.APPLICATION_JSON))

        client().fetchProfile("auth-code", REDIRECT_URI)

        server.verify()
    }

    @Test
    @DisplayName("이름이 없는 계정도 sub만으로 프로필을 만든다")
    fun 이름_없음() {
        expectTokenSuccess()
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("""{"sub": "42"}""", MediaType.APPLICATION_JSON))

        val profile = client().fetchProfile("auth-code", REDIRECT_URI)

        assertThat(profile.providerId).isEqualTo("42")
        assertThat(profile.nickname).isNull()
    }

    @Test
    @DisplayName("코드가 잘못되거나 이미 쓰여 구글이 400을 주면 401이고 사용자 정보는 조회하지 않는다")
    fun 코드_거절() {
        server.expect(requestTo(TOKEN_URI))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""{"error": "invalid_grant"}"""))

        assertThatThrownBy { client().fetchProfile("used-code", REDIRECT_URI) }
            .isInstanceOf(UnauthorizedException::class.java)
            .hasMessage(LOGIN_FAILED_MESSAGE)
        server.verify()
    }

    @Test
    @DisplayName("토큰 응답에 access_token이 없으면 401이다")
    fun 토큰_없음() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("""{"token_type": "Bearer"}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client().fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    @DisplayName("사용자 정보에 sub가 없거나 비어 있으면 401이다")
    fun sub_없음() {
        expectTokenSuccess()
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("""{"sub": " ", "name": "홍길동"}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client().fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    @DisplayName("구글 서버가 5xx를 주면 로그인 실패(401)가 아니라 공급자 문제(502용 예외)로 본다")
    fun 구글_서버_오류() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError())

        assertThatThrownBy { client().fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(SocialAuthUnavailableException::class.java)
            .hasMessageContaining("구글 서버와 통신하지 못했습니다.")
    }

    @Test
    @DisplayName("네트워크 오류도 공급자 문제로 본다")
    fun 네트워크_오류() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(IOException("connection reset")))

        assertThatThrownBy { client().fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(SocialAuthUnavailableException::class.java)
    }

    @Test
    @DisplayName("사용자 정보 조회가 실패해도 같은 기준으로 변환한다 (4xx는 401)")
    fun 사용자_정보_거절() {
        expectTokenSuccess()
        server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { client().fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    @DisplayName("클라이언트 ID나 시크릿이 비어 있으면 구글을 부르지 않고 설정 없음 예외를 던진다")
    fun 설정_없음() {
        assertThatThrownBy { client(clientId = "").fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(SocialAuthUnavailableException::class.java)
            .hasMessage("구글 로그인이 설정되지 않았습니다.")
        assertThatThrownBy { client(clientSecret = " ").fetchProfile("auth-code", REDIRECT_URI) }
            .isInstanceOf(SocialAuthUnavailableException::class.java)

        // 기대한 요청이 없는데 호출됐다면 MockRestServiceServer가 이미 실패했을 것이다
        server.verify()
    }
}
