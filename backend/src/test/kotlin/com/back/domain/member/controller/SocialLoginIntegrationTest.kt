package com.back.domain.member.controller

import com.back.domain.member.entity.MemberProvider
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// 소셜 로그인 통합 테스트. 실제 보안 필터, DB, GoogleOAuthClient(RestClient)를 모두 거치고,
// 구글 서버만 같은 프로세스의 가짜 HTTP 서버로 바꾼다. (코드 "user-a"는 sub "user-a"로 로그인되고, 그 외는 구글이 거절한 것으로 본다)
class SocialLoginIntegrationTest : MemberApiTestSupport() {

    companion object {
        private const val REDIRECT_URI = "http://localhost:3000/auth/callback/google"

        // 가짜 구글: 토큰 엔드포인트는 폼의 code가 "user-"로 시작하면 access_token을 주고 아니면 400, 사용자 정보는 토큰에서 sub를 꺼낸다.
        private val googleStub: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/token") { exchange ->
                val form = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                val code = form.split("&").map { it.split("=", limit = 2) }
                    .firstOrNull { it[0] == "code" }?.getOrNull(1)?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
                if (code != null && code.startsWith("user-")) {
                    respond(exchange, 200, """{"access_token": "token-$code", "token_type": "Bearer"}""")
                } else {
                    respond(exchange, 400, """{"error": "invalid_grant"}""")
                }
            }
            createContext("/userinfo") { exchange ->
                val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
                val sub = token?.removePrefix("token-")
                if (sub == null) {
                    respond(exchange, 401, """{"error": "invalid_token"}""")
                } else {
                    respond(exchange, 200, """{"sub": "$sub", "name": "구글 $sub", "email": "$sub@example.com"}""")
                }
            }
            start()
        }

        private fun respond(exchange: HttpExchange, status: Int, body: String) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun googleProperties(registry: DynamicPropertyRegistry) {
            val base = "http://localhost:${googleStub.address.port}"
            registry.add("oauth.google.client-id") { "test-client-id" }
            registry.add("oauth.google.client-secret") { "test-client-secret" }
            registry.add("oauth.google.token-uri") { "$base/token" }
            registry.add("oauth.google.user-info-uri") { "$base/userinfo" }
        }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            googleStub.stop(0)
        }
    }

    private fun socialLogin(code: String, provider: String = "google"): ResultActions =
        perform(HttpMethod.POST, "/api/auth/social/$provider", body = mapOf("code" to code, "redirectUri" to REDIRECT_URI))

    private fun socialLoginOk(code: String): Tokens {
        val result = socialLogin(code).andExpect(status().isOk())
        val node = json(result)
        return Tokens(node.get("accessToken").asString(), node.get("refreshToken").asString())
    }

    private fun me(tokens: Tokens) = json(perform(HttpMethod.GET, "/api/members/me", tokens.accessToken).andExpect(status().isOk()))

    @Test
    @DisplayName("처음 로그인하면 구글 회원이 만들어지고 발급된 토큰으로 내 정보를 조회할 수 있다")
    fun 첫_로그인_가입() {
        val tokens = socialLoginOk("user-a")

        val me = me(tokens)
        assertThat(me.get("provider").asString()).isEqualTo("GOOGLE")
        assertThat(me.get("username").asString()).isEqualTo("google_user-a")
        assertThat(me.get("nickname").asString()).isEqualTo("구글 user-a")
        val member = memberRepository.findByProviderAndProviderId(MemberProvider.GOOGLE, "user-a")
        assertThat(member).isNotNull
        assertThat(member!!.refreshToken).isEqualTo(tokens.refreshToken)
    }

    @Test
    @DisplayName("같은 구글 계정으로 다시 로그인하면 새 회원을 만들지 않고 같은 회원으로 로그인한다")
    fun 재로그인_같은_회원() {
        val first = me(socialLoginOk("user-b"))
        val second = me(socialLoginOk("user-b"))

        assertThat(second.get("memberId").asLong()).isEqualTo(first.get("memberId").asLong())
        assertThat(memberRepository.findAll().count { it.providerId == "user-b" }).isEqualTo(1)
    }

    @Test
    @DisplayName("다른 구글 계정은 다른 회원이 된다")
    fun 다른_계정은_다른_회원() {
        val a = me(socialLoginOk("user-c1"))
        val b = me(socialLoginOk("user-c2"))

        assertThat(a.get("memberId").asLong()).isNotEqualTo(b.get("memberId").asLong())
    }

    @Test
    @DisplayName("소셜 회원은 자동 생성된 아이디와 어떤 비밀번호로도 일반 로그인을 할 수 없다")
    fun 일반_로그인_불가() {
        socialLoginOk("user-d")

        login("google_user-d", PASSWORD).andExpect(status().isUnauthorized())
        login("google_user-d", "").andExpect(status().isBadRequest())
    }

    @Test
    @DisplayName("아이디/비밀번호 회원과는 합쳐지지 않는다 (닉네임이 같아도 별개 회원이다)")
    fun 일반_회원과_별개() {
        val localId = signUpOk("local-e", nickname = "구글 user-e")

        val social = me(socialLoginOk("user-e"))

        assertThat(social.get("memberId").asLong()).isNotEqualTo(localId)
        assertThat(social.get("provider").asString()).isEqualTo("GOOGLE")
    }

    @Test
    @DisplayName("발급된 리프레시 토큰으로 액세스 토큰을 재발급받을 수 있다")
    fun 리프레시_재발급() {
        val tokens = socialLoginOk("user-f")

        perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.refreshToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
    }

    @Test
    @DisplayName("구글이 코드를 거절하면 401이고 회원은 만들어지지 않는다")
    fun 코드_거절() {
        socialLogin("bad-code")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("구글 로그인에 실패했습니다. 다시 시도해주세요."))

        assertThat(memberRepository.findAll().none { it.provider == MemberProvider.GOOGLE }).isTrue()
    }

    @Test
    @DisplayName("지원하지 않는 공급자 주소는 400이고, 필수 값이 없으면 400이다")
    fun 잘못된_요청() {
        socialLogin("user-g", provider = "naver").andExpect(status().isBadRequest())
        socialLogin("user-g", provider = "local").andExpect(status().isBadRequest())
        perform(HttpMethod.POST, "/api/auth/social/google", body = mapOf("code" to "user-g"))
            .andExpect(status().isBadRequest())
    }

    @Test
    @DisplayName("로그인 없이 호출할 수 있다 (토큰 없는 요청도 401로 막히지 않는다)")
    fun 인증_없이_호출_가능() {
        perform(HttpMethod.POST, "/api/auth/social/google", body = mapOf("code" to "user-h", "redirectUri" to REDIRECT_URI))
            .andExpect(status().isOk())
    }

    @Test
    @DisplayName("탈퇴한 소셜 회원이 같은 구글 계정으로 다시 로그인하면 새 회원으로 가입되고 이전 회원은 식별자가 비워진 채 남는다")
    fun 탈퇴_후_재로그인은_새_회원() {
        val oldTokens = socialLoginOk("user-i")
        val oldMemberId = me(oldTokens).get("memberId").asLong()
        perform(HttpMethod.DELETE, "/api/members/me", oldTokens.accessToken).andExpect(status().isNoContent())

        val newTokens = socialLoginOk("user-i")

        val newMe = me(newTokens)
        assertThat(newMe.get("memberId").asLong()).isNotEqualTo(oldMemberId)
        assertThat(newMe.get("provider").asString()).isEqualTo("GOOGLE")
        val old = memberRepository.findById(oldMemberId).orElseThrow()
        assertThat(old.providerId).isNull()
        assertThat(old.provider).isEqualTo(MemberProvider.LOCAL)
        assertThat(old.username).isEqualTo("withdrawn_$oldMemberId")
        // 구글 식별자를 가진 회원은 새로 가입한 회원 한 명뿐이다.
        assertThat(memberRepository.findAll().count { it.providerId == "user-i" }).isEqualTo(1)
        // 이전 회원의 토큰은 계속 거절된다.
        perform(HttpMethod.GET, "/api/members/me", oldTokens.accessToken).andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("소셜 회원은 비밀번호를 바꿀 수 없다")
    fun 비밀번호_변경_불가() {
        val tokens = socialLoginOk("user-j")

        perform(
            HttpMethod.PATCH,
            "/api/members/me",
            tokens.accessToken,
            mapOf("nickname" to "새닉네임", "currentPassword" to "아무거나", "newPassword" to "newPassword1234"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("소셜 로그인 회원은 비밀번호를 변경할 수 없습니다."))
    }

    @Test
    @DisplayName("소셜 회원도 닉네임은 바꿀 수 있다")
    fun 닉네임_변경_가능() {
        val tokens = socialLoginOk("user-k")

        perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "새닉네임"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("새닉네임"))
            .andExpect(jsonPath("$.provider").value("GOOGLE"))
    }
}
