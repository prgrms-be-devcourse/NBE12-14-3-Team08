package com.back.domain.member.service

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.charset.StandardCharsets
import java.util.Date

// JWT 발급과 검증을 스프링 없이 확인한다. 만료·위조 토큰은 테스트에서 같은 키로 직접 만들어 쓴다.
class AuthTokenServiceTest {

    companion object {
        private const val SECRET = "local-development-jwt-secret-key-1234567890"
        private const val OTHER_SECRET = "another-development-jwt-secret-key-0987654321"
        private const val ACCESS_EXPIRATION = 1_800_000L
        private const val MEMBER_ID = 1L
    }

    private val service = AuthTokenService(SECRET, ACCESS_EXPIRATION)

    private val key = Keys.hmacShaKeyFor(SECRET.toByteArray(StandardCharsets.UTF_8))

    private fun claimsOf(token: String) =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    // 테스트가 원하는 클레임으로 서버 키를 써서 서명한 토큰을 만든다.
    private fun signedToken(subject: String = "1", tokenType: String? = "access", expiresInMillis: Long? = 60_000L): String {
        val builder = Jwts.builder().subject(subject).issuedAt(Date())
        if (tokenType != null) builder.claim("tokenType", tokenType)
        if (expiresInMillis != null) builder.expiration(Date(System.currentTimeMillis() + expiresInMillis))
        return builder.signWith(key).compact()
    }

    @Nested
    @DisplayName("액세스 토큰")
    inner class AccessToken {

        @Test
        @DisplayName("회원 id와 access 타입, 발급·만료 시각을 담고 서명이 유효하다")
        fun 발급() {
            val token = service.createAccessToken(MEMBER_ID)

            val claims = claimsOf(token)
            assertThat(claims.subject).isEqualTo("1")
            assertThat(claims["tokenType"]).isEqualTo("access")
            assertThat(claims.issuedAt).isNotNull()
            assertThat(claims.expiration).isNotNull()
            assertThat(service.validateToken(token)).isTrue()
            assertThat(service.getMemberId(token)).isEqualTo(MEMBER_ID)
        }

        @Test
        @DisplayName("만료 시각은 발급 시각 + 설정한 유효 시간이다 (JWT는 초 단위라 1초까지 오차)")
        fun 유효_시간() {
            val claims = claimsOf(service.createAccessToken(MEMBER_ID))

            val lifetimeSeconds = (claims.expiration.time - claims.issuedAt.time) / 1000
            assertThat(lifetimeSeconds).isBetween(ACCESS_EXPIRATION / 1000 - 1, ACCESS_EXPIRATION / 1000 + 1)
        }

        @Test
        @DisplayName("access 토큰은 isAccessToken만 true다")
        fun 타입_구분() {
            val token = service.createAccessToken(MEMBER_ID)

            assertThat(service.isAccessToken(token)).isTrue()
            assertThat(service.isRefreshToken(token)).isFalse()
        }

        @Test
        @DisplayName("유효 시간이 지난 액세스 토큰은 유효하지 않다")
        fun 만료된_토큰() {
            val expired = AuthTokenService(SECRET, -1_000L).createAccessToken(MEMBER_ID)

            assertThat(service.validateToken(expired)).isFalse()
        }

        @Test
        @DisplayName("유효 시간이 0이면 발급하자마자 만료된다")
        fun 유효_시간_0() {
            val token = AuthTokenService(SECRET, 0L).createAccessToken(MEMBER_ID)

            assertThat(service.validateToken(token)).isFalse()
        }

        @Test
        @DisplayName("회원 id가 다르면 subject도 다르다")
        fun 회원별로_다르다() {
            assertThat(service.getMemberId(service.createAccessToken(1L))).isEqualTo(1L)
            assertThat(service.getMemberId(service.createAccessToken(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE)
        }
    }

    @Nested
    @DisplayName("리프레시 토큰")
    inner class RefreshToken {

        @Test
        @DisplayName("회원 id와 refresh 타입을 담고 만료 시각은 없다")
        fun 발급() {
            val token = service.createRefreshToken(MEMBER_ID)

            val claims = claimsOf(token)
            assertThat(claims.subject).isEqualTo("1")
            assertThat(claims["tokenType"]).isEqualTo("refresh")
            assertThat(claims.expiration).isNull()
            assertThat(service.validateToken(token)).isTrue()
            assertThat(service.getMemberId(token)).isEqualTo(MEMBER_ID)
        }

        @Test
        @DisplayName("refresh 토큰은 isRefreshToken만 true다")
        fun 타입_구분() {
            val token = service.createRefreshToken(MEMBER_ID)

            assertThat(service.isRefreshToken(token)).isTrue()
            assertThat(service.isAccessToken(token)).isFalse()
        }

        @Test
        @DisplayName("유효 시간 설정이 달라도 리프레시 토큰은 만료되지 않는다")
        fun 만료_설정과_무관하다() {
            val token = AuthTokenService(SECRET, -1_000L).createRefreshToken(MEMBER_ID)

            assertThat(service.validateToken(token)).isTrue()
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    inner class Validate {

        @ParameterizedTest(name = "token=\"{0}\"")
        @ValueSource(strings = ["", " ", "invalid-token", "a.b.c", "a.b"])
        @DisplayName("토큰 모양이 아닌 문자열은 예외 없이 false다")
        fun 잘못된_모양(token: String) {
            assertThat(service.validateToken(token)).isFalse()
        }

        @Test
        @DisplayName("서명 부분을 바꾼 토큰은 false다")
        fun 서명_변조() {
            val token = service.createAccessToken(MEMBER_ID)
            val tampered = token.dropLast(4) + (if (token.endsWith("AAAA")) "BBBB" else "AAAA")

            assertThat(service.validateToken(tampered)).isFalse()
        }

        @Test
        @DisplayName("내용(subject)을 바꾸고 서명은 그대로 둔 토큰은 false다")
        fun 내용_변조() {
            val parts = service.createAccessToken(MEMBER_ID).split(".")
            val forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"sub":"2","tokenType":"access"}""".toByteArray())

            assertThat(service.validateToken("${parts[0]}.$forgedPayload.${parts[2]}")).isFalse()
        }

        @Test
        @DisplayName("다른 비밀키로 서명한 토큰은 false다")
        fun 다른_키() {
            val other = AuthTokenService(OTHER_SECRET, ACCESS_EXPIRATION).createAccessToken(MEMBER_ID)

            assertThat(service.validateToken(other)).isFalse()
        }

        @Test
        @DisplayName("서명이 없는(alg=none) 토큰은 false다")
        fun 서명_없는_토큰() {
            val unsigned = Jwts.builder().subject("1").claim("tokenType", "access").compact()

            assertThat(service.validateToken(unsigned)).isFalse()
        }

        @Test
        @DisplayName("subject가 숫자가 아니어도 서명이 맞으면 validateToken은 true지만 getMemberId는 NumberFormatException이다")
        fun 숫자가_아닌_subject() {
            val token = signedToken(subject = "not-a-number")

            assertThat(service.validateToken(token)).isTrue()
            assertThatThrownBy { service.getMemberId(token) }.isInstanceOf(NumberFormatException::class.java)
        }

        @Test
        @DisplayName("서명이 맞아도 tokenType이 없으면 access도 refresh도 아니다")
        fun 타입_없는_토큰() {
            val token = signedToken(tokenType = null)

            assertThat(service.validateToken(token)).isTrue()
            assertThat(service.isAccessToken(token)).isFalse()
            assertThat(service.isRefreshToken(token)).isFalse()
        }

        @Test
        @DisplayName("tokenType이 알 수 없는 값이면 access도 refresh도 아니다")
        fun 알_수_없는_타입() {
            val token = signedToken(tokenType = "admin")

            assertThat(service.isAccessToken(token)).isFalse()
            assertThat(service.isRefreshToken(token)).isFalse()
        }

        @Test
        @DisplayName("tokenType 대소문자가 다르면 구분한다")
        fun 타입은_대소문자를_구분한다() {
            val token = signedToken(tokenType = "ACCESS")

            assertThat(service.isAccessToken(token)).isFalse()
        }

        @Test
        @DisplayName("검증 없이 타입이나 id를 묻는 메서드는 잘못된 토큰에 JwtException을 던진다 (호출 전에 validateToken으로 걸러야 한다)")
        fun 검증하지_않은_토큰() {
            assertThatThrownBy { service.isAccessToken("invalid-token") }.isInstanceOf(JwtException::class.java)
            assertThatThrownBy { service.isRefreshToken("invalid-token") }.isInstanceOf(JwtException::class.java)
            assertThatThrownBy { service.getMemberId("invalid-token") }.isInstanceOf(JwtException::class.java)
        }

        @Test
        @DisplayName("만료된 토큰의 getMemberId는 JwtException이다")
        fun 만료된_토큰의_id() {
            val expired = AuthTokenService(SECRET, -1_000L).createAccessToken(MEMBER_ID)

            assertThatThrownBy { service.getMemberId(expired) }.isInstanceOf(JwtException::class.java)
        }
    }

    @Nested
    @DisplayName("비밀키 설정")
    inner class SecretKey {

        @Test
        @DisplayName("32바이트보다 짧은 비밀키는 서비스를 만들 때 거절한다")
        fun 짧은_비밀키() {
            assertThatThrownBy { AuthTokenService("too-short", ACCESS_EXPIRATION) }
                .isInstanceOf(JwtException::class.java)
        }

        @Test
        @DisplayName("같은 비밀키를 쓰는 다른 인스턴스가 발급한 토큰도 검증된다 (서버를 여러 대 띄워도 동작한다)")
        fun 같은_비밀키면_인스턴스가_달라도_검증된다() {
            val token = AuthTokenService(SECRET, ACCESS_EXPIRATION).createAccessToken(MEMBER_ID)

            assertThat(AuthTokenService(SECRET, ACCESS_EXPIRATION).validateToken(token)).isTrue()
        }
    }
}
