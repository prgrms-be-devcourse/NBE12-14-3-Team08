package com.back.domain.member.social

import com.back.domain.member.entity.MemberProvider
import com.back.global.exception.BusinessRuleException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

// 소셜 로그인 진입점. 공급자 호출은 트랜잭션 밖에서 하고, 회원 조회·생성만 SocialMemberService의 트랜잭션에서 한다.
@Service
class SocialAuthService(
    clients: List<SocialOAuthClient>,
    private val socialMemberService: SocialMemberService,
) {

    private val clientsByProvider: Map<MemberProvider, SocialOAuthClient> = clients.associateBy { it.provider }

    // 소셜 로그인에 성공한 회원의 id를 돌려준다. 토큰 발급은 일반 로그인과 같은 방식으로 호출한 쪽에서 한다.
    fun login(providerName: String, code: String, redirectUri: String): Long {
        val provider = parseProvider(providerName)
        val client = clientsByProvider[provider] ?: throw BusinessRuleException(UNSUPPORTED_MESSAGE)

        val profile = client.fetchProfile(code, redirectUri)

        return try {
            socialMemberService.findOrCreate(provider, profile)
        } catch (e: DataIntegrityViolationException) {
            // 같은 계정으로 첫 로그인이 동시에 들어와 다른 요청이 먼저 회원을 만든 경우: 이제는 그 회원을 찾는다.
            socialMemberService.findOrCreate(provider, profile)
        }
    }

    private fun parseProvider(name: String): MemberProvider =
        MemberProvider.entries.firstOrNull { it != MemberProvider.LOCAL && it.name.equals(name, ignoreCase = true) }
            ?: throw BusinessRuleException(UNSUPPORTED_MESSAGE)

    companion object {
        private const val UNSUPPORTED_MESSAGE = "지원하지 않는 로그인 방식입니다."
    }
}
