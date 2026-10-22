package com.back.domain.member.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Service
class AuthTokenService(
    @param:Value("\${jwt.secret}") secret: String,
    @param:Value("\${jwt.access-expiration}") private val accessExpiration: Long,
) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun createAccessToken(memberId: Long): String {
        val now = Date()

        return Jwts.builder()
            .subject(memberId.toString()) // 토큰을 발급받은 회원의 ID 저장
            .claim("tokenType", "access") // 액세스 토큰과 리프레시 토큰을 구분
            .issuedAt(now)
            .expiration(Date(now.time + accessExpiration))
            .signWith(secretKey)
            .compact()
    }

    fun createRefreshToken(memberId: Long): String {
        val now = Date()

        return Jwts.builder()
            .subject(memberId.toString())
            .claim("tokenType", "refresh")
            .issuedAt(now)
            // 리프레시 토큰에는 만료시간을 설정하지 않음
            .signWith(secretKey)
            .compact()
    }

    fun getMemberId(token: String): Long = getClaims(token).subject.toLong()

    // 위조 여부와 액세스 토큰의 만료 여부 검증
    fun validateToken(token: String): Boolean =
        try {
            getClaims(token)
            true
        } catch (exception: JwtException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }

    fun isAccessToken(token: String): Boolean = "access" == getClaims(token)["tokenType"]

    fun isRefreshToken(token: String): Boolean = "refresh" == getClaims(token)["tokenType"]

    private fun getClaims(token: String): Claims =
        Jwts.parser() // 서버의 비밀키로 토큰 서명 검증
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
