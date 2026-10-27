package com.back.domain.member.social

import com.back.domain.member.entity.MemberProvider
import com.back.global.exception.SocialAuthUnavailableException
import com.back.global.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

// 구글 로그인(OpenID Connect 인가 코드 방식). 요청 범위는 openid profile뿐이고 이메일은 받지 않는다.
@Component
class GoogleOAuthClient(
    private val socialRestClient: RestClient,
    @param:Value("\${oauth.google.client-id:}") private val clientId: String,
    @param:Value("\${oauth.google.client-secret:}") private val clientSecret: String,
    @param:Value("\${oauth.google.token-uri:https://oauth2.googleapis.com/token}") private val tokenUri: String,
    @param:Value("\${oauth.google.user-info-uri:https://openidconnect.googleapis.com/v1/userinfo}")
    private val userInfoUri: String,
) : SocialOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override val provider: MemberProvider = MemberProvider.GOOGLE

    override fun fetchProfile(code: String, redirectUri: String): SocialProfile {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw SocialAuthUnavailableException("구글 로그인이 설정되지 않았습니다.")
        }

        val accessToken = requestAccessToken(code, redirectUri)
        val userInfo = requestUserInfo(accessToken)

        val subject = (userInfo["sub"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw UnauthorizedException(LOGIN_FAILED_MESSAGE)

        return SocialProfile(subject, userInfo["name"] as? String)
    }

    private fun requestAccessToken(code: String, redirectUri: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", redirectUri)
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }

        val response = callGoogle("토큰 교환") {
            socialRestClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE)
        }

        return (response?.get("access_token") as? String)?.takeIf { it.isNotBlank() }
            ?: throw UnauthorizedException(LOGIN_FAILED_MESSAGE)
    }

    private fun requestUserInfo(accessToken: String): Map<String, Any?> =
        callGoogle("사용자 정보 조회") {
            socialRestClient.get()
                .uri(userInfoUri)
                .headers { it.setBearerAuth(accessToken) }
                .retrieve()
                .body(MAP_TYPE)
        } ?: throw UnauthorizedException(LOGIN_FAILED_MESSAGE)

    // 4xx는 코드가 잘못됐거나 이미 쓰인 경우라 로그인 실패(401)로, 그 밖의 통신 실패는 공급자 문제(502)로 나눈다.
    // 응답 본문에는 토큰이 들어 있을 수 있어 로그에는 상태 코드만 남긴다.
    private fun <T> callGoogle(step: String, call: () -> T): T =
        try {
            call()
        } catch (e: HttpClientErrorException) {
            log.warn("구글 {} 실패: status={}", step, e.statusCode)
            throw UnauthorizedException(LOGIN_FAILED_MESSAGE)
        } catch (e: RestClientException) {
            log.warn("구글 {} 통신 실패: {}", step, e.javaClass.simpleName)
            throw SocialAuthUnavailableException("구글 서버와 통신하지 못했습니다. 잠시 후 다시 시도해주세요.", e)
        }

    companion object {
        private const val LOGIN_FAILED_MESSAGE = "구글 로그인에 실패했습니다. 다시 시도해주세요."
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any?>>() {}
    }
}
