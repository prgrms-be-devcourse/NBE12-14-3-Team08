package com.back.domain.member.social

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberProvider
import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.global.entity.requiredId
import com.back.global.exception.UnauthorizedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 소셜 로그인으로 들어온 사용자를 회원과 연결한다. 이메일로 기존 계정과 합치지 않고, 공급자 + 공급자 식별자로만 찾는다.
// 외부 호출(공급자 통신)은 DB 트랜잭션 밖에서 끝낸 뒤 이 서비스를 부른다.
@Service
class SocialMemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenService: AuthTokenService,
) {

    // 이미 가입한 회원이면 그 회원의 id를, 아니면 새 회원을 만들어 id를 돌려준다.
    @Transactional
    fun findOrCreate(provider: MemberProvider, profile: SocialProfile): Long {
        if (profile.providerId.length > PROVIDER_ID_MAX_LENGTH) {
            throw UnauthorizedException("소셜 계정 정보가 올바르지 않습니다.")
        }

        val existing = memberRepository.findByProviderAndProviderId(provider, profile.providerId)
        if (existing != null) {
            return loginExisting(existing)
        }

        val member = Member.createSocial(
            provider = provider,
            providerId = profile.providerId,
            nickname = nicknameOf(provider, profile),
            username = availableUsername(provider, profile.providerId),
            encodedUnusablePassword = unusablePassword(),
        )
        val saved = memberRepository.save(member)
        saved.updateRefreshToken(authTokenService.createRefreshToken(saved.requiredId))

        return saved.requiredId
    }

    private fun loginExisting(member: Member): Long {
        // 일반 로그인과 같이 탈퇴한 회원은 다시 로그인할 수 없다.
        if (member.status == MemberStatus.WITHDRAWN) {
            throw UnauthorizedException("탈퇴한 회원은 로그인할 수 없습니다.")
        }

        if (member.refreshToken.isNullOrBlank()) {
            member.updateRefreshToken(authTokenService.createRefreshToken(member.requiredId))
        }

        return member.requiredId
    }

    // 소셜 회원의 아이디는 사용자가 정하지 않으므로 공급자 이름과 식별자로 만든다. 같은 아이디를 먼저 쓰는 회원이 있으면 뒤에 임의 값을 붙인다.
    private fun availableUsername(provider: MemberProvider, providerId: String): String {
        val base = "${provider.name.lowercase()}_$providerId"
        if (!memberRepository.existsByUsername(base)) return base

        return "${base}_${UUID.randomUUID().toString().take(6)}"
    }

    private fun nicknameOf(provider: MemberProvider, profile: SocialProfile): String =
        profile.nickname?.trim()?.takeIf { it.isNotEmpty() }?.take(NICKNAME_MAX_LENGTH)
            ?: defaultNickname(provider)

    private fun defaultNickname(provider: MemberProvider): String =
        when (provider) {
            MemberProvider.GOOGLE -> "구글 사용자"
            MemberProvider.LOCAL -> "사용자"
        }

    // 아무도 알 수 없는 값의 해시. 이 비밀번호로는 일반 로그인을 할 수 없다.
    private fun unusablePassword(): String =
        checkNotNull(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID())) {
            "비밀번호를 암호화하지 못했습니다."
        }

    companion object {
        private const val NICKNAME_MAX_LENGTH = 50
        private const val PROVIDER_ID_MAX_LENGTH = 100
    }
}
